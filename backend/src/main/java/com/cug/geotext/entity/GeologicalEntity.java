package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("entity")
public class GeologicalEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long chunkId;
    private String entityName;
    private String entityType;
    private BigDecimal confidence;
    private String sourceText;
    private Integer page;
    private Integer sourceStart;
    private Integer sourceEnd;
    private String provider;
    private String model;
    private Long dictionaryId;
    private String standardName;
    private String normalizationStatus;
    private OffsetDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSourceStart() { return sourceStart; }
    public void setSourceStart(Integer sourceStart) { this.sourceStart = sourceStart; }
    public Integer getSourceEnd() { return sourceEnd; }
    public void setSourceEnd(Integer sourceEnd) { this.sourceEnd = sourceEnd; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getDictionaryId() { return dictionaryId; }
    public void setDictionaryId(Long dictionaryId) { this.dictionaryId = dictionaryId; }
    public String getStandardName() { return standardName; }
    public void setStandardName(String standardName) { this.standardName = standardName; }
    public String getNormalizationStatus() { return normalizationStatus; }
    public void setNormalizationStatus(String normalizationStatus) { this.normalizationStatus = normalizationStatus; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
}
