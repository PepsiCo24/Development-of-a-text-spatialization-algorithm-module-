package com.cug.geotext.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServiceConfig {
    @Bean
    RestClient aiRestClient(@Value("${app.ai-service.base-url:http://localhost:8000}") String baseUrl, AiAuditInterceptor audit) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofSeconds(10)).withReadTimeout(Duration.ofMinutes(10));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(ClientHttpRequestFactories.get(settings)).requestInterceptor(audit).build();
    }

    @Bean("parsingExecutor")
    Executor parsingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("document-parse-");
        executor.initialize();
        return executor;
    }
}

