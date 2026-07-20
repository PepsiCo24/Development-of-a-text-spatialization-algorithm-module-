package com.cug.geotext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("system_log")
public class SystemLog {
    @TableId(type=IdType.AUTO)private Long id;private Long userId;private String module;private String action;private String requestMethod;private String requestPath;private String clientIp;private String status;private String errorMessage;private Integer elapsedMs;private String provider;private String model;private String functionName;private OffsetDateTime createTime;
    public Long getId(){return id;}public void setId(Long v){id=v;}public Long getUserId(){return userId;}public void setUserId(Long v){userId=v;}public String getModule(){return module;}public void setModule(String v){module=v;}public String getAction(){return action;}public void setAction(String v){action=v;}public String getRequestMethod(){return requestMethod;}public void setRequestMethod(String v){requestMethod=v;}public String getRequestPath(){return requestPath;}public void setRequestPath(String v){requestPath=v;}public String getClientIp(){return clientIp;}public void setClientIp(String v){clientIp=v;}public String getStatus(){return status;}public void setStatus(String v){status=v;}public String getErrorMessage(){return errorMessage;}public void setErrorMessage(String v){errorMessage=v;}public Integer getElapsedMs(){return elapsedMs;}public void setElapsedMs(Integer v){elapsedMs=v;}public String getProvider(){return provider;}public void setProvider(String v){provider=v;}public String getModel(){return model;}public void setModel(String v){model=v;}public String getFunctionName(){return functionName;}public void setFunctionName(String v){functionName=v;}public OffsetDateTime getCreateTime(){return createTime;}public void setCreateTime(OffsetDateTime v){createTime=v;}
}
