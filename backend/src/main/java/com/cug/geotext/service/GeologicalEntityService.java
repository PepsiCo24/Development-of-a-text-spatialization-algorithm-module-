package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.client.AiEntityResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.ManualEntityRequest;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalEntityMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeologicalEntityService {
    private final GeologicalEntityMapper mapper;
    public GeologicalEntityService(GeologicalEntityMapper mapper) { this.mapper = mapper; }

    public List<GeologicalEntity> list(long documentId) {
        return mapper.selectList(new LambdaQueryWrapper<GeologicalEntity>()
            .eq(GeologicalEntity::getDocumentId, documentId)
            .orderByAsc(GeologicalEntity::getPage, GeologicalEntity::getSourceStart, GeologicalEntity::getId));
    }

    @Transactional
    public GeologicalEntity create(long documentId, ManualEntityRequest request) {
        GeologicalEntity entity = new GeologicalEntity();
        entity.setDocumentId(documentId);
        entity.setCreateTime(OffsetDateTime.now());
        applyManual(entity, request);
        mapper.insert(entity);
        return entity;
    }

    @Transactional
    public GeologicalEntity update(long documentId, long entityId, ManualEntityRequest request) {
        GeologicalEntity entity = require(documentId, entityId);
        applyManual(entity, request);
        mapper.updateById(entity);
        return entity;
    }

    @Transactional
    public GeologicalEntity review(long documentId, long entityId, String reviewStatus) {
        GeologicalEntity entity = require(documentId, entityId);
        entity.setReviewStatus(reviewStatus);
        mapper.updateById(entity);
        return entity;
    }

    @Transactional
    public void delete(long documentId, long entityId) { mapper.deleteById(require(documentId, entityId).getId()); }

    private GeologicalEntity require(long documentId, long entityId) {
        GeologicalEntity entity = mapper.selectById(entityId);
        if (entity == null || !Long.valueOf(documentId).equals(entity.getDocumentId())) throw new BusinessException(404, "Entity was not found");
        return entity;
    }

    private void applyManual(GeologicalEntity entity, ManualEntityRequest request) {
        entity.setChunkId(request.chunkId()); entity.setEntityName(request.entityName().trim()); entity.setEntityType(request.entityType());
        entity.setConfidence(request.confidence()); entity.setSourceText(request.sourceText()); entity.setPage(request.page());
        entity.setSourceStart(request.sourceStart()); entity.setSourceEnd(request.sourceEnd()); entity.setProvider("manual"); entity.setModel("manual-review");
        entity.setReviewStatus(reviewStatus(request.reviewStatus()));
    }

    private String reviewStatus(String value) {
        String status = value == null || value.isBlank() ? "PENDING" : value;
        if (!List.of("PENDING", "CONFIRMED", "REJECTED").contains(status)) throw new BusinessException(400, "Invalid review status");
        return status;
    }

    @Transactional
    public void replace(long documentId, String provider, String model, List<AiEntityResponse.AiEntity> sources) {
        mapper.delete(new LambdaQueryWrapper<GeologicalEntity>().eq(GeologicalEntity::getDocumentId, documentId));
        OffsetDateTime now = OffsetDateTime.now();
        for (AiEntityResponse.AiEntity source : sources) {
            GeologicalEntity entity = new GeologicalEntity();
            entity.setDocumentId(documentId);
            entity.setChunkId(source.chunkId());
            entity.setEntityName(source.entityName());
            entity.setEntityType(source.entityType());
            entity.setConfidence(source.confidence());
            entity.setSourceText(source.sourceText());
            entity.setPage(source.page());
            entity.setSourceStart(source.sourceStart());
            entity.setSourceEnd(source.sourceEnd());
            entity.setProvider(provider);
            entity.setModel(model);
            entity.setReviewStatus("PENDING");
            entity.setCreateTime(now);
            mapper.insert(entity);
        }
    }
}
