package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("entity_relation")
public class EntityRelation {
    @TableId(type = IdType.AUTO) private Long id;
    private Long documentId;
    private Long sourceEntityId;
    private Long targetEntityId;
    private String relationType;
    private BigDecimal confidence;
    private String sourceText;
    private Integer page;
    private String provider;
    private String model;
    private OffsetDateTime createTime;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public Long getDocumentId(){return documentId;} public void setDocumentId(Long value){documentId=value;}
    public Long getSourceEntityId(){return sourceEntityId;} public void setSourceEntityId(Long value){sourceEntityId=value;}
    public Long getTargetEntityId(){return targetEntityId;} public void setTargetEntityId(Long value){targetEntityId=value;}
    public String getRelationType(){return relationType;} public void setRelationType(String value){relationType=value;}
    public BigDecimal getConfidence(){return confidence;} public void setConfidence(BigDecimal value){confidence=value;}
    public String getSourceText(){return sourceText;} public void setSourceText(String value){sourceText=value;}
    public Integer getPage(){return page;} public void setPage(Integer value){page=value;}
    public String getProvider(){return provider;} public void setProvider(String value){provider=value;}
    public String getModel(){return model;} public void setModel(String value){model=value;}
    public OffsetDateTime getCreateTime(){return createTime;} public void setCreateTime(OffsetDateTime value){createTime=value;}
}
