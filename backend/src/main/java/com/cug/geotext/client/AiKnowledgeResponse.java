package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record AiKnowledgeResponse(String provider, String model, List<AiAttribute> attributes, List<AiRelation> relations) {
    public record AiAttribute(
        @JsonProperty("entity_id") Long entityId,
        @JsonProperty("attribute_type") String attributeType,
        @JsonProperty("original_value") String originalValue,
        BigDecimal confidence,
        @JsonProperty("source_text") String sourceText,
        Integer page
    ) {}
    public record AiRelation(
        @JsonProperty("source_entity_id") Long sourceEntityId,
        @JsonProperty("target_entity_id") Long targetEntityId,
        @JsonProperty("relation_type") String relationType,
        BigDecimal confidence,
        @JsonProperty("source_text") String sourceText,
        Integer page
    ) {}
}
