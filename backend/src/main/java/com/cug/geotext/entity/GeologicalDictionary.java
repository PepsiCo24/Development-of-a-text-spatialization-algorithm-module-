package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("dictionary")
public class GeologicalDictionary {
    @TableId(type = IdType.AUTO) private Long id;
    private String termType;
    private String standardName;
    private String aliases;
    private String description;
    private Boolean enabled;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public String getTermType(){return termType;} public void setTermType(String value){termType=value;}
    public String getStandardName(){return standardName;} public void setStandardName(String value){standardName=value;}
    public String getAliases(){return aliases;} public void setAliases(String value){aliases=value;}
    public String getDescription(){return description;} public void setDescription(String value){description=value;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean value){enabled=value;}
    public OffsetDateTime getCreateTime(){return createTime;} public void setCreateTime(OffsetDateTime value){createTime=value;}
    public OffsetDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(OffsetDateTime value){updateTime=value;}
}
