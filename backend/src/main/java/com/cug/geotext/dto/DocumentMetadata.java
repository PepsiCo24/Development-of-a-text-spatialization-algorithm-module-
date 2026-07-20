package com.cug.geotext.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record DocumentMetadata(
    @Size(max = 255, message = "资料名称不能超过255个字符") String name,
    @Size(max = 128, message = "区域不能超过128个字符") String region,
    @Min(value = 1800, message = "年份不能早于1800年") @Max(value = 2100, message = "年份不能晚于2100年") Integer year,
    @Size(max = 1000, message = "关键词不能超过1000个字符") String keyword,
    @Size(max = 5000, message = "摘要不能超过5000个字符") String summary
) {}

