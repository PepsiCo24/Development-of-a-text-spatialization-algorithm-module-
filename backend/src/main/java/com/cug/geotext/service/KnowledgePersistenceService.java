package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.client.AiKnowledgeResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.ManualAttributeRequest;
import com.cug.geotext.dto.ManualRelationRequest;
import com.cug.geotext.entity.EntityAttribute;
import com.cug.geotext.entity.EntityRelation;
import com.cug.geotext.mapper.EntityAttributeMapper;
import com.cug.geotext.mapper.EntityRelationMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePersistenceService {
    private final EntityAttributeMapper attributeMapper;
    private final EntityRelationMapper relationMapper;
    public KnowledgePersistenceService(EntityAttributeMapper attributeMapper, EntityRelationMapper relationMapper) {
        this.attributeMapper = attributeMapper; this.relationMapper = relationMapper;
    }
    public List<EntityAttribute> attributes(long documentId) {
        return attributeMapper.selectList(new LambdaQueryWrapper<EntityAttribute>().eq(EntityAttribute::getDocumentId, documentId).orderByAsc(EntityAttribute::getPage, EntityAttribute::getId));
    }
    public List<EntityRelation> relations(long documentId) {
        return relationMapper.selectList(new LambdaQueryWrapper<EntityRelation>().eq(EntityRelation::getDocumentId, documentId).orderByAsc(EntityRelation::getPage, EntityRelation::getId));
    }
    @Transactional public EntityAttribute createAttribute(long documentId, ManualAttributeRequest request) { EntityAttribute item = new EntityAttribute(); item.setDocumentId(documentId); item.setCreateTime(OffsetDateTime.now()); apply(item, request); attributeMapper.insert(item); return item; }
    @Transactional public EntityAttribute updateAttribute(long documentId, long id, ManualAttributeRequest request) { EntityAttribute item = requireAttribute(documentId, id); apply(item, request); attributeMapper.updateById(item); return item; }
    @Transactional public EntityAttribute reviewAttribute(long documentId, long id, String status) { EntityAttribute item = requireAttribute(documentId, id); item.setReviewStatus(reviewStatus(status)); attributeMapper.updateById(item); return item; }
    @Transactional public void deleteAttribute(long documentId, long id) { attributeMapper.deleteById(requireAttribute(documentId, id).getId()); }

    @Transactional public EntityRelation createRelation(long documentId, ManualRelationRequest request) { if (request.sourceEntityId().equals(request.targetEntityId())) throw new BusinessException(400, "Relation endpoints must differ"); EntityRelation item = new EntityRelation(); item.setDocumentId(documentId); item.setCreateTime(OffsetDateTime.now()); apply(item, request); relationMapper.insert(item); return item; }
    @Transactional public EntityRelation updateRelation(long documentId, long id, ManualRelationRequest request) { if (request.sourceEntityId().equals(request.targetEntityId())) throw new BusinessException(400, "Relation endpoints must differ"); EntityRelation item = requireRelation(documentId, id); apply(item, request); relationMapper.updateById(item); return item; }
    @Transactional public EntityRelation reviewRelation(long documentId, long id, String status) { EntityRelation item = requireRelation(documentId, id); item.setReviewStatus(reviewStatus(status)); relationMapper.updateById(item); return item; }
    @Transactional public void deleteRelation(long documentId, long id) { relationMapper.deleteById(requireRelation(documentId, id).getId()); }

    private EntityAttribute requireAttribute(long documentId, long id) { EntityAttribute item = attributeMapper.selectById(id); if (item == null || !Long.valueOf(documentId).equals(item.getDocumentId())) throw new BusinessException(404, "Attribute was not found"); return item; }
    private EntityRelation requireRelation(long documentId, long id) { EntityRelation item = relationMapper.selectById(id); if (item == null || !Long.valueOf(documentId).equals(item.getDocumentId())) throw new BusinessException(404, "Relation was not found"); return item; }
    private void apply(EntityAttribute item, ManualAttributeRequest request) { item.setEntityId(request.entityId()); item.setAttributeType(request.attributeType()); item.setOriginalValue(request.originalValue()); item.setConfidence(request.confidence()); item.setSourceText(request.sourceText()); item.setPage(request.page()); item.setProvider("manual"); item.setModel("manual-review"); item.setReviewStatus(reviewStatus(request.reviewStatus())); }
    private void apply(EntityRelation item, ManualRelationRequest request) { item.setSourceEntityId(request.sourceEntityId()); item.setTargetEntityId(request.targetEntityId()); item.setRelationType(request.relationType()); item.setConfidence(request.confidence()); item.setSourceText(request.sourceText()); item.setPage(request.page()); item.setProvider("manual"); item.setModel("manual-review"); item.setReviewStatus(reviewStatus(request.reviewStatus())); }
    private String reviewStatus(String value) { String status = value == null || value.isBlank() ? "PENDING" : value; if (!List.of("PENDING", "CONFIRMED", "REJECTED").contains(status)) throw new BusinessException(400, "Invalid review status"); return status; }
    @Transactional
    public void replace(long documentId, AiKnowledgeResponse response) {
        attributeMapper.delete(new LambdaQueryWrapper<EntityAttribute>().eq(EntityAttribute::getDocumentId, documentId));
        relationMapper.delete(new LambdaQueryWrapper<EntityRelation>().eq(EntityRelation::getDocumentId, documentId));
        OffsetDateTime now = OffsetDateTime.now();
        for (AiKnowledgeResponse.AiAttribute source : response.attributes()) {
            EntityAttribute item = new EntityAttribute(); item.setDocumentId(documentId); item.setEntityId(source.entityId());
            item.setAttributeType(source.attributeType()); item.setOriginalValue(source.originalValue()); item.setConfidence(source.confidence());
            item.setSourceText(source.sourceText()); item.setPage(source.page()); item.setProvider(response.provider()); item.setModel(response.model()); item.setReviewStatus("PENDING"); item.setCreateTime(now);
            attributeMapper.insert(item);
        }
        for (AiKnowledgeResponse.AiRelation source : response.relations()) {
            EntityRelation item = new EntityRelation(); item.setDocumentId(documentId); item.setSourceEntityId(source.sourceEntityId()); item.setTargetEntityId(source.targetEntityId());
            item.setRelationType(source.relationType()); item.setConfidence(source.confidence()); item.setSourceText(source.sourceText()); item.setPage(source.page());
            item.setProvider(response.provider()); item.setModel(response.model()); item.setReviewStatus("PENDING"); item.setCreateTime(now); relationMapper.insert(item);
        }
    }
}
