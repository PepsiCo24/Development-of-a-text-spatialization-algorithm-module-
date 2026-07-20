package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.LlmConfigRequest;
import com.cug.geotext.entity.LlmConfig;
import com.cug.geotext.mapper.LlmConfigMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class LlmConfigService {
    private final LlmConfigMapper mapper;private final RestClient ai;public LlmConfigService(LlmConfigMapper mapper,RestClient ai){this.mapper=mapper;this.ai=ai;}
    public List<ConfigView>list(){return mapper.selectList(new LambdaQueryWrapper<LlmConfig>().orderByAsc(LlmConfig::getProvider)).stream().map(this::view).toList();}
    public ConfigView save(LlmConfigRequest request){LlmConfig config=mapper.selectOne(new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getProvider,request.provider()));boolean created=config==null;if(created){config=new LlmConfig();config.setProvider(request.provider());config.setCreateTime(OffsetDateTime.now());}config.setBaseUrl(request.baseUrl());config.setModelName(request.modelName());if(request.apiKey()!=null&&!request.apiKey().isBlank())config.setApiKey(request.apiKey());config.setTemperature(request.temperature());config.setPromptTemplate(request.promptTemplate());config.setEnabled(request.enabled()==null||request.enabled());config.setUpdateTime(OffsetDateTime.now());if(created)mapper.insert(config);else mapper.updateById(config);if(Boolean.TRUE.equals(config.getEnabled()))apply(config);return view(config);}
    public ConfigView apply(long id){LlmConfig config=mapper.selectById(id);if(config==null)throw new BusinessException(404,"模型配置不存在");apply(config);return view(config);}
    private void apply(LlmConfig config){if(config.getApiKey()==null||config.getApiKey().isBlank())throw new BusinessException(409,"请先配置 API Key");Map<String,Object>body=new LinkedHashMap<>();body.put("provider",config.getProvider());body.put("base_url",config.getBaseUrl());body.put("api_key",config.getApiKey());body.put("model",config.getModelName());body.put("temperature",config.getTemperature());if(config.getPromptTemplate()!=null)body.put("prompt_template",config.getPromptTemplate());ai.put().uri("/api/v1/config/provider").body(body).retrieve().toBodilessEntity();}
    private ConfigView view(LlmConfig c){return new ConfigView(c.getId(),c.getProvider(),c.getBaseUrl(),c.getModelName(),c.getApiKey()!=null&&!c.getApiKey().isBlank(),c.getTemperature(),c.getPromptTemplate(),c.getEnabled(),c.getUpdateTime());}
    public record ConfigView(Long id,String provider,String baseUrl,String modelName,boolean keyConfigured,java.math.BigDecimal temperature,String promptTemplate,Boolean enabled,OffsetDateTime updateTime){}
}
