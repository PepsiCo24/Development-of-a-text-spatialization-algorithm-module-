package com.cug.geotext.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI geotextOpenApi() {
        return new OpenAPI().info(new Info().title("基于填图对象智能识别的文本空间化算法模块 API").version("0.1.0").description("地质文本空间化算法模块接口"));
    }
}

