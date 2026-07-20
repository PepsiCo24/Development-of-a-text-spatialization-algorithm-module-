package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record AiEntityResponse(String provider, String model, List<AiEntity> entities) {
    public record AiEntity(
        @JsonProperty("entity_name") String entityName,
        @JsonProperty("entity_type") String entityType,
        BigDecimal confidence,
        @JsonProperty("source_text") String sourceText,
        Integer page,
        @JsonProperty("chunk_id") Long chunkId,
        @JsonProperty("source_start") Integer sourceStart,
        @JsonProperty("source_end") Integer sourceEnd
    ) {}
}
