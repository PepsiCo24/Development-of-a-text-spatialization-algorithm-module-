package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public record AiSpatialResponse(String provider,String model,List<AiSpatialObject> objects,List<String> warnings){
    public record AiSpatialObject(String name,@JsonProperty("object_type")String objectType,@JsonProperty("entity_id")Long entityId,
        @JsonProperty("chunk_id")Long chunkId,JsonNode geometry,BigDecimal confidence,@JsonProperty("source_text")String sourceText,
        Integer page,@JsonProperty("geocoding_source")String geocodingSource){}
}
