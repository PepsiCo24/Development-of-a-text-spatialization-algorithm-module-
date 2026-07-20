package com.cug.geotext.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DocumentQuery(
    String query,
    String type,
    String region,
    Integer year,
    String status,
    @Min(value = 1, message = "页码必须大于0") Integer page,
    @Min(value = 1, message = "每页数量必须大于0") @Max(value = 100, message = "每页最多100条") Integer size
) {
    public int pageOrDefault() { return page == null ? 1 : page; }
    public int sizeOrDefault() { return size == null ? 20 : size; }
}

