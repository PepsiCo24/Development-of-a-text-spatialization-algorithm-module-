package com.cug.geotext.service;

import com.cug.geotext.client.AiSpatialResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.SpatialObject;
import com.cug.geotext.mapper.SpatialObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpatialObjectService {
    private final SpatialObjectMapper mapper; private final ObjectMapper objectMapper;
    public SpatialObjectService(SpatialObjectMapper mapper,ObjectMapper objectMapper){this.mapper=mapper;this.objectMapper=objectMapper;}
    public List<SpatialObject> list(Long documentId){return mapper.selectAll(documentId);}
    public SpatialObject get(long id){SpatialObject object=mapper.selectOne(id);if(object==null)throw new BusinessException(404,"空间对象不存在");return object;}
    @Transactional public void replace(long documentId,AiSpatialResponse response){mapper.deleteByDocument(documentId);OffsetDateTime now=OffsetDateTime.now();for(AiSpatialResponse.AiSpatialObject source:response.objects()){
        SpatialObject object=new SpatialObject();object.setDocumentId(documentId);object.setEntityId(source.entityId());object.setChunkId(source.chunkId());object.setName(source.name());object.setObjectType(source.objectType());
        object.setGeometryType(source.geometry().path("type").asText());try{object.setGeojson(objectMapper.writeValueAsString(source.geometry()));}catch(JsonProcessingException e){throw new IllegalArgumentException("空间几何序列化失败",e);}
        object.setConfidence(source.confidence());object.setSourceText(source.sourceText());object.setPage(source.page());object.setGeocodingSource(source.geocodingSource());object.setProvider(response.provider());object.setModel(response.model());object.setCreateTime(now);mapper.insert(object);
    }}
}
