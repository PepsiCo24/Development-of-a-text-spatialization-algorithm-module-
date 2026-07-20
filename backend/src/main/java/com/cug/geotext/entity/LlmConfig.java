package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("llm_config")
public class LlmConfig {
    @TableId(type=IdType.AUTO)private Long id;private String provider;private String baseUrl;private String modelName;@JsonIgnore private String apiKey;private BigDecimal temperature;private String promptTemplate;private Boolean enabled;private OffsetDateTime createTime;private OffsetDateTime updateTime;
    public Long getId(){return id;}public void setId(Long v){id=v;}public String getProvider(){return provider;}public void setProvider(String v){provider=v;}public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String v){baseUrl=v;}public String getModelName(){return modelName;}public void setModelName(String v){modelName=v;}public String getApiKey(){return apiKey;}public void setApiKey(String v){apiKey=v;}public BigDecimal getTemperature(){return temperature;}public void setTemperature(BigDecimal v){temperature=v;}public String getPromptTemplate(){return promptTemplate;}public void setPromptTemplate(String v){promptTemplate=v;}public Boolean getEnabled(){return enabled;}public void setEnabled(Boolean v){enabled=v;}public OffsetDateTime getCreateTime(){return createTime;}public void setCreateTime(OffsetDateTime v){createTime=v;}public OffsetDateTime getUpdateTime(){return updateTime;}public void setUpdateTime(OffsetDateTime v){updateTime=v;}
}
