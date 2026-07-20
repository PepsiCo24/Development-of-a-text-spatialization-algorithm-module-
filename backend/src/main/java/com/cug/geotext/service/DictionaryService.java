package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.DictionaryRequest;
import com.cug.geotext.entity.GeologicalDictionary;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDictionaryMapper;
import com.cug.geotext.mapper.GeologicalEntityMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService {
    private final GeologicalDictionaryMapper dictionaryMapper;
    private final GeologicalEntityMapper entityMapper;
    public DictionaryService(GeologicalDictionaryMapper dictionaryMapper, GeologicalEntityMapper entityMapper) {
        this.dictionaryMapper = dictionaryMapper; this.entityMapper = entityMapper;
    }
    public List<GeologicalDictionary> list(String query, String type) {
        LambdaQueryWrapper<GeologicalDictionary> wrapper = new LambdaQueryWrapper<GeologicalDictionary>()
            .eq(type != null && !type.isBlank(), GeologicalDictionary::getTermType, type)
            .and(query != null && !query.isBlank(), nested -> nested.like(GeologicalDictionary::getStandardName, query).or().like(GeologicalDictionary::getAliases, query))
            .orderByAsc(GeologicalDictionary::getTermType, GeologicalDictionary::getStandardName);
        return dictionaryMapper.selectList(wrapper);
    }
    public GeologicalDictionary create(DictionaryRequest request) {
        GeologicalDictionary item = apply(new GeologicalDictionary(), request); item.setCreateTime(OffsetDateTime.now()); dictionaryMapper.insert(item); return item;
    }
    public GeologicalDictionary update(long id, DictionaryRequest request) {
        GeologicalDictionary item = dictionaryMapper.selectById(id); if (item == null) throw new BusinessException(404, "词典条目不存在");
        apply(item, request); dictionaryMapper.updateById(item); return item;
    }
    public void delete(long id) { if (dictionaryMapper.deleteById(id) == 0) throw new BusinessException(404, "词典条目不存在"); }

    public int normalize(List<GeologicalEntity> entities) {
        List<GeologicalDictionary> dictionary = dictionaryMapper.selectList(new LambdaQueryWrapper<GeologicalDictionary>().eq(GeologicalDictionary::getEnabled, true));
        int matched = 0;
        for (GeologicalEntity entity : entities) {
            GeologicalDictionary match = null; String status = "UNMATCHED";
            String name = normalized(entity.getEntityName());
            for (GeologicalDictionary item : dictionary) {
                if (!item.getTermType().equalsIgnoreCase(entity.getEntityType())) continue;
                if (normalized(item.getStandardName()).equals(name)) { match = item; status = "EXACT"; break; }
                boolean alias = Arrays.stream((item.getAliases() == null ? "" : item.getAliases()).split("\\|"))
                    .map(this::normalized).anyMatch(name::equals);
                if (alias) { match = item; status = "ALIAS"; break; }
            }
            entity.setDictionaryId(match == null ? null : match.getId());
            entity.setStandardName(match == null ? entity.getEntityName() : match.getStandardName());
            entity.setNormalizationStatus(status);
            entityMapper.updateById(entity);
            if (match != null) matched++;
        }
        return matched;
    }
    private GeologicalDictionary apply(GeologicalDictionary item, DictionaryRequest request) {
        item.setTermType(request.termType().trim().toUpperCase(Locale.ROOT)); item.setStandardName(request.standardName().trim());
        item.setAliases(request.aliases() == null ? null : request.aliases().trim()); item.setDescription(request.description());
        item.setEnabled(request.enabled() == null || request.enabled()); item.setUpdateTime(OffsetDateTime.now()); return item;
    }
    private String normalized(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s·•]", ""); }
}
