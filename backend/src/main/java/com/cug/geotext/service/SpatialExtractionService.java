package com.cug.geotext.service;

import com.cug.geotext.client.AiSpatialResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SpatialExtractionService {
    private final GeologicalDocumentMapper documentMapper;private final DocumentService documentService;private final DocumentChunkService chunkService;
    private final GeologicalEntityService entityService;private final SpatialObjectService spatialService;private final LlmConfigService llmConfigService;private final RestClient aiRestClient;private final Executor executor;
    public SpatialExtractionService(GeologicalDocumentMapper documentMapper,DocumentService documentService,DocumentChunkService chunkService,GeologicalEntityService entityService,SpatialObjectService spatialService,LlmConfigService llmConfigService,RestClient aiRestClient,@Qualifier("parsingExecutor")Executor executor){
        this.documentMapper=documentMapper;this.documentService=documentService;this.chunkService=chunkService;this.entityService=entityService;this.spatialService=spatialService;this.llmConfigService=llmConfigService;this.aiRestClient=aiRestClient;this.executor=executor;}
    public SpatialStatus start(long documentId,String provider){GeologicalDocument d=documentService.get(documentId);if(!"COMPLETED".equals(d.getKnowledgeStatus()))throw new BusinessException(409,"请先完成属性关系抽取与术语标准化");if("EXTRACTING".equals(d.getSpatialStatus()))throw new BusinessException(409,"空间化任务正在进行中");d.setGraphStatus("PENDING");d.setGraphProgress(0);d.setGraphError(null);d.setGraphNodeCount(0);d.setGraphRelationCount(0);d.setVectorChunkCount(0);d.setGraphSyncedAt(null);update(d,"EXTRACTING",5,"");executor.execute(()->extract(documentId,provider));return status(documentId);}
    public SpatialStatus status(long documentId){GeologicalDocument d=documentService.get(documentId);if("EXTRACTING".equals(d.getSpatialStatus())&&d.getUpdateTime()!=null&&d.getUpdateTime().isBefore(OffsetDateTime.now().minusMinutes(2))){update(d,"FAILED",zero(d.getSpatialProgress()),"空间化任务已中断，请重新启动");}return new SpatialStatus(documentId,d.getSpatialStatus()==null?"PENDING":d.getSpatialStatus(),zero(d.getSpatialProgress()),d.getSpatialError(),d.getSpatialWarnings(),zero(d.getSpatialObjectCount()),d.getSpatialExtractedAt());}
    private void extract(long documentId,String provider){GeologicalDocument d=null;try{d=documentService.get(documentId);List<DocumentChunk>chunks=chunkService.list(documentId);List<GeologicalEntity>entities=entityService.list(documentId);if(chunks.isEmpty())throw new IllegalStateException("没有可空间化的文本块");
        update(d,"EXTRACTING",15,"");llmConfigService.applyProvider(provider);update(d,"EXTRACTING",35,"");Map<Long,List<GeologicalEntity>>byChunk=entities.stream().collect(Collectors.groupingBy(GeologicalEntity::getChunkId));List<Map<String,Object>>requestChunks=chunks.stream().map(chunk->{Map<String,Object>m=new LinkedHashMap<>();m.put("chunk_id",chunk.getId());m.put("content",chunk.getContent());m.put("page_start",chunk.getPageStart());m.put("page_end",chunk.getPageEnd());m.put("entities",byChunk.getOrDefault(chunk.getId(),List.of()).stream().map(e->Map.of("entity_id",e.getId(),"entity_name",e.getStandardName()==null?e.getEntityName():e.getStandardName(),"entity_type",e.getEntityType())).toList());return m;}).toList();
        Map<String,Object>request=new LinkedHashMap<>();request.put("document_id",documentId);if(provider!=null&&!provider.isBlank())request.put("provider",provider);if(d.getRegion()!=null&&!d.getRegion().isBlank())request.put("region_hint",d.getRegion());request.put("chunks",requestChunks);
        AiSpatialResponse response=aiRestClient.post().uri("/api/v1/spatial/extract").body(request).retrieve().body(AiSpatialResponse.class);if(response==null||response.objects()==null)throw new IllegalStateException("AI 服务未返回空间对象结果");
        update(d,"EXTRACTING",80,"");spatialService.replace(documentId,response);d.setSpatialStatus("COMPLETED");d.setSpatialProgress(100);d.setSpatialError("");d.setSpatialWarnings(response.warnings()==null?"":String.join("；",response.warnings()));d.setSpatialObjectCount(response.objects().size());d.setSpatialExtractedAt(OffsetDateTime.now());d.setUpdateTime(OffsetDateTime.now());documentMapper.updateById(d);
    }catch(Exception exception){d=documentMapper.selectById(documentId);if(d!=null){String msg=exception instanceof RestClientResponseException r?describeAiError(r):exception.getMessage();update(d,"FAILED",zero(d.getSpatialProgress()),abbreviate(msg));}}}
    private void update(GeologicalDocument d,String state,int progress,String error){d.setSpatialStatus(state);d.setSpatialProgress(progress);d.setSpatialError(error);d.setUpdateTime(OffsetDateTime.now());documentMapper.updateById(d);}private int zero(Integer v){return v==null?0:v;}private String describeAiError(RestClientResponseException e){String body=e.getResponseBodyAsString();String prefix="AI 空间化服务返回 HTTP "+e.getStatusCode().value();return body==null||body.isBlank()?prefix:prefix+": "+body;}private String abbreviate(String v){String m=v==null||v.isBlank()?"文本空间化失败":v;return m.length()<=1000?m:m.substring(0,1000);}
    public record SpatialStatus(long documentId,String status,int progress,String errorMessage,String warnings,int objectCount,OffsetDateTime extractedAt){}
}
