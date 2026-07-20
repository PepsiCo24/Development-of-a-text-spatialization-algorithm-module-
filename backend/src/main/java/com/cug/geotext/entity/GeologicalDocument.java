package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;

@TableName("document")
public class GeologicalDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String region;
    private Integer year;
    private String keyword;
    private String summary;
    @JsonIgnore
    private String filePath;
    private String originalName;
    private String contentType;
    private String status;
    private Integer parseProgress;
    private String errorMessage;
    private Integer pageCount;
    private Integer chunkCount;
    private OffsetDateTime parsedAt;
    private String entityStatus;
    private Integer entityProgress;
    private String entityError;
    private Integer entityCount;
    private OffsetDateTime entityExtractedAt;
    private String knowledgeStatus;
    private Integer knowledgeProgress;
    private String knowledgeError;
    private Integer attributeCount;
    private Integer relationCount;
    private Integer normalizedCount;
    private OffsetDateTime knowledgeExtractedAt;
    private String spatialStatus;
    private Integer spatialProgress;
    private String spatialError;
    private String spatialWarnings;
    private Integer spatialObjectCount;
    private OffsetDateTime spatialExtractedAt;
    private Long fileSize;
    @JsonIgnore
    private Long createdBy;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getParseProgress() { return parseProgress; }
    public void setParseProgress(Integer parseProgress) { this.parseProgress = parseProgress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public OffsetDateTime getParsedAt() { return parsedAt; }
    public void setParsedAt(OffsetDateTime parsedAt) { this.parsedAt = parsedAt; }
    public String getEntityStatus() { return entityStatus; }
    public void setEntityStatus(String entityStatus) { this.entityStatus = entityStatus; }
    public Integer getEntityProgress() { return entityProgress; }
    public void setEntityProgress(Integer entityProgress) { this.entityProgress = entityProgress; }
    public String getEntityError() { return entityError; }
    public void setEntityError(String entityError) { this.entityError = entityError; }
    public Integer getEntityCount() { return entityCount; }
    public void setEntityCount(Integer entityCount) { this.entityCount = entityCount; }
    public OffsetDateTime getEntityExtractedAt() { return entityExtractedAt; }
    public void setEntityExtractedAt(OffsetDateTime entityExtractedAt) { this.entityExtractedAt = entityExtractedAt; }
    public String getKnowledgeStatus() { return knowledgeStatus; }
    public void setKnowledgeStatus(String knowledgeStatus) { this.knowledgeStatus = knowledgeStatus; }
    public Integer getKnowledgeProgress() { return knowledgeProgress; }
    public void setKnowledgeProgress(Integer knowledgeProgress) { this.knowledgeProgress = knowledgeProgress; }
    public String getKnowledgeError() { return knowledgeError; }
    public void setKnowledgeError(String knowledgeError) { this.knowledgeError = knowledgeError; }
    public Integer getAttributeCount() { return attributeCount; }
    public void setAttributeCount(Integer attributeCount) { this.attributeCount = attributeCount; }
    public Integer getRelationCount() { return relationCount; }
    public void setRelationCount(Integer relationCount) { this.relationCount = relationCount; }
    public Integer getNormalizedCount() { return normalizedCount; }
    public void setNormalizedCount(Integer normalizedCount) { this.normalizedCount = normalizedCount; }
    public OffsetDateTime getKnowledgeExtractedAt() { return knowledgeExtractedAt; }
    public void setKnowledgeExtractedAt(OffsetDateTime knowledgeExtractedAt) { this.knowledgeExtractedAt = knowledgeExtractedAt; }
    public String getSpatialStatus() { return spatialStatus; }
    public void setSpatialStatus(String spatialStatus) { this.spatialStatus = spatialStatus; }
    public Integer getSpatialProgress() { return spatialProgress; }
    public void setSpatialProgress(Integer spatialProgress) { this.spatialProgress = spatialProgress; }
    public String getSpatialError() { return spatialError; }
    public void setSpatialError(String spatialError) { this.spatialError = spatialError; }
    public String getSpatialWarnings() { return spatialWarnings; }
    public void setSpatialWarnings(String spatialWarnings) { this.spatialWarnings = spatialWarnings; }
    public Integer getSpatialObjectCount() { return spatialObjectCount; }
    public void setSpatialObjectCount(Integer spatialObjectCount) { this.spatialObjectCount = spatialObjectCount; }
    public OffsetDateTime getSpatialExtractedAt() { return spatialExtractedAt; }
    public void setSpatialExtractedAt(OffsetDateTime spatialExtractedAt) { this.spatialExtractedAt = spatialExtractedAt; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreateTime() { return createTime; }
    public void setCreateTime(OffsetDateTime createTime) { this.createTime = createTime; }
    public OffsetDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(OffsetDateTime updateTime) { this.updateTime = updateTime; }
}
