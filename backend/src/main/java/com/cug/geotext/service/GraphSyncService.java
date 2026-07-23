package com.cug.geotext.service;

import com.cug.geotext.client.AiGraphSyncResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.*;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GraphSyncService {
    private static final Set<String> GRAPH_RELATIONS=Set.of("LOCATED_IN","OCCURS_IN","INTRUDES","CONTACTS","CONTROLS","CONTAINS");
    private final GeologicalDocumentMapper documentMapper; private final DocumentService documentService; private final DocumentChunkService chunkService;
    private final GeologicalEntityService entityService; private final KnowledgePersistenceService knowledgeService; private final SpatialObjectService spatialService;
    private final RestClient aiRestClient; private final Executor executor;
    public GraphSyncService(GeologicalDocumentMapper documentMapper,DocumentService documentService,DocumentChunkService chunkService,GeologicalEntityService entityService,KnowledgePersistenceService knowledgeService,SpatialObjectService spatialService,RestClient aiRestClient,@Qualifier("parsingExecutor")Executor executor){
        this.documentMapper=documentMapper;this.documentService=documentService;this.chunkService=chunkService;this.entityService=entityService;this.knowledgeService=knowledgeService;this.spatialService=spatialService;this.aiRestClient=aiRestClient;this.executor=executor;}

    public GraphStatus start(long documentId){GeologicalDocument d=documentService.get(documentId);if(!"COMPLETED".equals(d.getSpatialStatus()))throw new BusinessException(409,"请先完成文本空间化");if("SYNCING".equals(d.getGraphStatus())&&d.getUpdateTime()!=null&&d.getUpdateTime().isAfter(OffsetDateTime.now().minusMinutes(2)))throw new BusinessException(409,"图谱同步正在进行中");update(d,"SYNCING",5,"");executor.execute(()->sync(documentId));return status(documentId);}
    public GraphStatus status(long documentId){GeologicalDocument d=documentService.get(documentId);return new GraphStatus(documentId,d.getGraphStatus()==null?"PENDING":d.getGraphStatus(),zero(d.getGraphProgress()),d.getGraphError(),zero(d.getGraphNodeCount()),zero(d.getGraphRelationCount()),zero(d.getVectorChunkCount()),d.getGraphSyncedAt());}

    private void sync(long documentId){GeologicalDocument d=null;try{d=documentService.get(documentId);List<GeologicalEntity>entities=entityService.list(documentId);List<EntityRelation>relations=knowledgeService.relations(documentId);List<DocumentChunk>chunks=chunkService.list(documentId);List<SpatialObject>spatial=spatialService.list(documentId);
        Map<Long,SpatialObject>locations=new HashMap<>();for(SpatialObject object:spatial)if(object.getEntityId()!=null&&object.getCenterLongitude()!=null&&object.getCenterLatitude()!=null)locations.putIfAbsent(object.getEntityId(),object);
        List<Map<String,Object>>nodes=new ArrayList<>();Set<Long>nodeIds=new HashSet<>();for(GeologicalEntity entity:entities){String type=nodeType(entity.getEntityType());if(type==null)continue;Map<String,Object>node=new LinkedHashMap<>();node.put("entity_id",entity.getId());node.put("document_id",documentId);node.put("name",entity.getStandardName()==null?entity.getEntityName():entity.getStandardName());node.put("node_type",type);node.put("source_text",entity.getSourceText());node.put("page",entity.getPage());SpatialObject location=locations.get(entity.getId());node.put("longitude",location==null?null:location.getCenterLongitude());node.put("latitude",location==null?null:location.getCenterLatitude());nodes.add(node);nodeIds.add(entity.getId());}
        List<Map<String,Object>>edges=relations.stream().filter(r->GRAPH_RELATIONS.contains(r.getRelationType())&&nodeIds.contains(r.getSourceEntityId())&&nodeIds.contains(r.getTargetEntityId())).map(r->{Map<String,Object>m=new LinkedHashMap<>();m.put("source_entity_id",r.getSourceEntityId());m.put("target_entity_id",r.getTargetEntityId());m.put("relation_type",r.getRelationType());m.put("confidence",r.getConfidence());m.put("source_text",r.getSourceText());m.put("page",r.getPage());return m;}).toList();
        String documentName=d.getName();List<Map<String,Object>>vectors=chunks.stream().map(c->Map.<String,Object>of("chunk_id",c.getId(),"document_id",documentId,"document_name",documentName,"content",c.getContent(),"page_start",c.getPageStart(),"page_end",c.getPageEnd())).toList();
        update(d,"SYNCING",35,"");Map<String,Object>request=Map.of("document_id",documentId,"nodes",nodes,"relations",edges,"chunks",vectors);AiGraphSyncResponse response=aiRestClient.post().uri("/api/v1/graph/sync").body(request).retrieve().body(AiGraphSyncResponse.class);if(response==null)throw new IllegalStateException("AI 服务未返回图谱同步结果");
        d.setGraphStatus("COMPLETED");d.setGraphProgress(100);d.setGraphError(null);d.setGraphNodeCount(response.nodeCount());d.setGraphRelationCount(response.relationCount());d.setVectorChunkCount(response.vectorCount());d.setGraphSyncedAt(OffsetDateTime.now());d.setUpdateTime(OffsetDateTime.now());documentMapper.updateById(d);
    }catch(Exception exception){d=documentMapper.selectById(documentId);if(d!=null){String message=exception instanceof RestClientResponseException r?"图谱服务返回 HTTP "+r.getStatusCode().value()+responseDetail(r):exception.getMessage();update(d,"FAILED",zero(d.getGraphProgress()),abbreviate(message));}}}

    private String nodeType(String type){return switch(type){case "STRATUM"->"STRATUM";case "ROCK_BODY","LITHOLOGY"->"ROCK_BODY";case "FAULT"->"STRUCTURE";case "ORE_BODY","MINERALIZATION_ZONE"->"ORE_BODY";case "MINERAL"->"MINERAL";case "PLACE"->"REGION";default->null;};}
    private String responseDetail(RestClientResponseException exception){String body=exception.getResponseBodyAsString();return body==null||body.isBlank()?"":": "+body;}
    private void update(GeologicalDocument d,String status,int progress,String error){d.setGraphStatus(status);d.setGraphProgress(progress);d.setGraphError(error);d.setUpdateTime(OffsetDateTime.now());documentMapper.updateById(d);}private int zero(Integer value){return value==null?0:value;}private String abbreviate(String value){String message=value==null||value.isBlank()?"知识图谱同步失败":value;return message.length()>1000?message.substring(0,1000):message;}
    public record GraphStatus(long documentId,String status,int progress,String errorMessage,int nodeCount,int relationCount,int vectorCount,OffsetDateTime syncedAt){}
}
