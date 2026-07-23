package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

public record AiQuestionResponse(String answer, @JsonAlias("related_entities") List<Map<String,Object>> relatedEntities, @JsonAlias("spatial_locations") List<Map<String,Object>> spatialLocations, List<Map<String,Object>> sources, String provider, String model) {}
