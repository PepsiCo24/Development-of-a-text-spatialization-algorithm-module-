package com.cug.geotext.dto;
import jakarta.validation.constraints.*;import java.math.BigDecimal;
public record LlmConfigRequest(@NotBlank@Pattern(regexp="deepseek|qwen")String provider,@NotBlank@Size(max=500)String baseUrl,@NotBlank@Size(max=128)String modelName,@Size(max=500)String apiKey,@NotNull@DecimalMin("0")@DecimalMax("2")BigDecimal temperature,@Size(max=8000)String promptTemplate,Boolean enabled){}
