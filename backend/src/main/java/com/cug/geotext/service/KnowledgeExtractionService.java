package com.cug.geotext.service;

import com.cug.geotext.client.AiKnowledgeResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.EntityAttribute;
import com.cug.geotext.entity.EntityRelation;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KnowledgeExtractionService {
    private final GeologicalDocumentMapper documentMapper;
    private final DocumentService documentService;
    private final DocumentChunkService chunkService;
    private final GeologicalEntityService entityService;
    private final KnowledgePersistenceService persistence;
    private final DictionaryService dictionaryService;
    private final LlmConfigService llmConfigService;
    private final RestClient aiRestClient;
    private final Executor executor;

    public KnowledgeExtractionService(GeologicalDocumentMapper documentMapper, DocumentService documentService,
        DocumentChunkService chunkService, GeologicalEntityService entityService, KnowledgePersistenceService persistence,
        DictionaryService dictionaryService, LlmConfigService llmConfigService, RestClient aiRestClient, @Qualifier("parsingExecutor") Executor executor) {
        this.documentMapper=documentMapper; this.documentService=documentService; this.chunkService=chunkService;
        this.entityService=entityService; this.persistence=persistence; this.dictionaryService=dictionaryService; this.llmConfigService=llmConfigService;
        this.aiRestClient=aiRestClient; this.executor=executor;
    }

    public KnowledgeStatus start(long documentId, String provider) {
        GeologicalDocument document = documentService.get(documentId);
        if (!"COMPLETED".equals(document.getEntityStatus())) throw new BusinessException(409, "请先完成地质实体识别");
        if ("EXTRACTING".equals(document.getKnowledgeStatus())) throw new BusinessException(409, "属性关系抽取正在进行中");
        document.setSpatialStatus("PENDING");document.setSpatialProgress(0);document.setSpatialError(null);document.setSpatialWarnings(null);document.setSpatialObjectCount(0);document.setSpatialExtractedAt(null);document.setGraphStatus("PENDING");document.setGraphProgress(0);document.setGraphError(null);document.setGraphNodeCount(0);document.setGraphRelationCount(0);document.setVectorChunkCount(0);document.setGraphSyncedAt(null);
        updateState(document, "EXTRACTING", 5, "");
        executor.execute(() -> extract(documentId, provider));
        return status(documentId);
    }
    public KnowledgeStatus status(long documentId) {
        GeologicalDocument d=documentService.get(documentId);
        if("EXTRACTING".equals(d.getKnowledgeStatus())&&d.getUpdateTime()!=null&&d.getUpdateTime().isBefore(OffsetDateTime.now().minusMinutes(2)))
            updateState(d,"FAILED",zero(d.getKnowledgeProgress()),"知识抽取任务已中断，请重新启动");
        return new KnowledgeStatus(documentId, d.getKnowledgeStatus()==null?"PENDING":d.getKnowledgeStatus(), zero(d.getKnowledgeProgress()), d.getKnowledgeError(), zero(d.getAttributeCount()), zero(d.getRelationCount()), zero(d.getNormalizedCount()), d.getKnowledgeExtractedAt());
    }
    public KnowledgeResult result(long documentId) {
        documentService.get(documentId);
        return new KnowledgeResult(entityService.list(documentId), persistence.attributes(documentId), persistence.relations(documentId));
    }
    private void extract(long documentId, String provider) {
        GeologicalDocument document=null;
        try {
            document=documentService.get(documentId);
            List<DocumentChunk> chunks=chunkService.list(documentId); List<GeologicalEntity> entities=entityService.list(documentId);
            if(chunks.isEmpty()||entities.isEmpty()) throw new IllegalStateException("没有可抽取的文本块或实体");
            updateState(document,"EXTRACTING",15,"");
            llmConfigService.applyProvider(provider);
            updateState(document,"EXTRACTING",35,"");
            Map<Long,List<GeologicalEntity>> byChunk=entities.stream().collect(java.util.stream.Collectors.groupingBy(GeologicalEntity::getChunkId));
            List<Map<String,Object>> requestChunks=chunks.stream().filter(c->byChunk.containsKey(c.getId())).map(chunk->{
                Map<String,Object> item=new LinkedHashMap<>(); item.put("chunk_id",chunk.getId()); item.put("content",chunk.getContent()); item.put("page_start",chunk.getPageStart()); item.put("page_end",chunk.getPageEnd());
                item.put("entities",byChunk.get(chunk.getId()).stream().map(e->Map.of("entity_id",e.getId(),"entity_name",e.getEntityName(),"entity_type",e.getEntityType())).toList()); return item;
            }).toList();
            if(requestChunks.isEmpty()) throw new IllegalStateException("实体与文本块来源无法关联");
            Map<String,Object> request=new LinkedHashMap<>(); request.put("document_id",documentId); if(provider!=null&&!provider.isBlank())request.put("provider",provider); request.put("chunks",requestChunks);
            AiKnowledgeResponse response=aiRestClient.post().uri("/api/v1/knowledge/extract").body(request).retrieve().body(AiKnowledgeResponse.class);
            if(response==null||response.attributes()==null||response.relations()==null) throw new IllegalStateException("AI 服务未返回属性关系结果");
            updateState(document,"EXTRACTING",75,""); persistence.replace(documentId,response);
            int normalized=dictionaryService.normalize(entities);
            document.setKnowledgeStatus("COMPLETED"); document.setKnowledgeProgress(100); document.setKnowledgeError("");
            document.setAttributeCount(response.attributes().size()); document.setRelationCount(response.relations().size()); document.setNormalizedCount(normalized);
            document.setKnowledgeExtractedAt(OffsetDateTime.now()); document.setUpdateTime(OffsetDateTime.now()); documentMapper.updateById(document);
        } catch(Exception exception) {
            document=documentMapper.selectById(documentId); if(document!=null){String message=exception instanceof RestClientResponseException r?describeAiError(r):exception.getMessage(); updateState(document,"FAILED",zero(document.getKnowledgeProgress()),abbreviate(message));}
        }
    }
    private void updateState(GeologicalDocument d,String state,int progress,String error){d.setKnowledgeStatus(state);d.setKnowledgeProgress(progress);d.setKnowledgeError(error);d.setUpdateTime(OffsetDateTime.now());documentMapper.updateById(d);}
    private int zero(Integer value){return value==null?0:value;}
    private String describeAiError(RestClientResponseException exception){String body=exception.getResponseBodyAsString();String prefix="AI 属性关系服务返回 HTTP "+exception.getStatusCode().value();return body==null||body.isBlank()?prefix:prefix+": "+body;}
    private String abbreviate(String value){String message=value==null||value.isBlank()?"属性关系抽取失败":value;return message.length()<=1000?message:message.substring(0,1000);}
    public record KnowledgeStatus(long documentId,String status,int progress,String errorMessage,int attributeCount,int relationCount,int normalizedCount,OffsetDateTime extractedAt){}
    public record KnowledgeResult(List<GeologicalEntity> entities,List<EntityAttribute> attributes,List<EntityRelation> relations){}
}
