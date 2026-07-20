package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.client.AiEntityResponse;
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
            entity.setCreateTime(now);
            mapper.insert(entity);
        }
    }
}
