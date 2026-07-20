package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.client.AiKnowledgeResponse;
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
    @Transactional
    public void replace(long documentId, AiKnowledgeResponse response) {
        attributeMapper.delete(new LambdaQueryWrapper<EntityAttribute>().eq(EntityAttribute::getDocumentId, documentId));
        relationMapper.delete(new LambdaQueryWrapper<EntityRelation>().eq(EntityRelation::getDocumentId, documentId));
        OffsetDateTime now = OffsetDateTime.now();
        for (AiKnowledgeResponse.AiAttribute source : response.attributes()) {
            EntityAttribute item = new EntityAttribute(); item.setDocumentId(documentId); item.setEntityId(source.entityId());
            item.setAttributeType(source.attributeType()); item.setOriginalValue(source.originalValue()); item.setConfidence(source.confidence());
            item.setSourceText(source.sourceText()); item.setPage(source.page()); item.setProvider(response.provider()); item.setModel(response.model()); item.setCreateTime(now);
            attributeMapper.insert(item);
        }
        for (AiKnowledgeResponse.AiRelation source : response.relations()) {
            EntityRelation item = new EntityRelation(); item.setDocumentId(documentId); item.setSourceEntityId(source.sourceEntityId()); item.setTargetEntityId(source.targetEntityId());
            item.setRelationType(source.relationType()); item.setConfidence(source.confidence()); item.setSourceText(source.sourceText()); item.setPage(source.page());
            item.setProvider(response.provider()); item.setModel(response.model()); item.setCreateTime(now); relationMapper.insert(item);
        }
    }
}
