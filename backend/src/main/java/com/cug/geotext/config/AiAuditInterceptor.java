package com.cug.geotext.config;

import com.cug.geotext.entity.LlmConfig;
import com.cug.geotext.mapper.LlmConfigMapper;
import com.cug.geotext.service.SystemLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.stereotype.Component;

@Component
public class AiAuditInterceptor implements ClientHttpRequestInterceptor {
    private final SystemLogService logs;private final LlmConfigMapper configs;private final ObjectMapper json;public AiAuditInterceptor(SystemLogService logs,LlmConfigMapper configs,ObjectMapper json){this.logs=logs;this.configs=configs;this.json=json;}
    @Override public ClientHttpResponse intercept(HttpRequest request,byte[]body,ClientHttpRequestExecution execution)throws IOException{long start=System.nanoTime();String provider=null,model=null,error=null,status="SUCCESS";try{if(isJson(request.getHeaders().getContentType())&&body.length>0){JsonNode node=json.readTree(new String(body,StandardCharsets.UTF_8));provider=node.path("provider").asText(null);if(provider!=null){LlmConfig config=configs.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getProvider,provider));model=config==null?null:config.getModelName();}}ClientHttpResponse response=execution.execute(request,body);if(response.getStatusCode().isError())status="FAILED";record(request,start,status,error,provider,model);return response;}catch(IOException|RuntimeException ex){status="FAILED";error=ex.getMessage();record(request,start,status,error,provider,model);throw ex;}}
    private boolean isJson(MediaType contentType){return contentType!=null&&(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)||contentType.getSubtype().endsWith("+json"));}
    private void record(HttpRequest request,long start,String status,String error,String provider,String model){String path=request.getURI().getPath();String function=path.replaceFirst("^/api/v1/","");logs.record("AI","调用 "+path,request.getMethod().name(),path,status,error,(int)((System.nanoTime()-start)/1_000_000),provider,model,function);}
}
