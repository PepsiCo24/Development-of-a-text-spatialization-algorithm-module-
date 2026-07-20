package com.cug.geotext.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentStatusRequest(@NotBlank(message = "状态不能为空") String status) {}

