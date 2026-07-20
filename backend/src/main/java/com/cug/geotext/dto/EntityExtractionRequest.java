package com.cug.geotext.dto;

import jakarta.validation.constraints.Pattern;

public record EntityExtractionRequest(
    @Pattern(regexp = "deepseek|qwen", message = "provider 仅支持 deepseek 或 qwen") String provider
) {}
