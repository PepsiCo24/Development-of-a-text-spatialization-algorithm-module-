package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiGraphSyncResponse(@JsonProperty("node_count") int nodeCount, @JsonProperty("relation_count") int relationCount, @JsonProperty("vector_count") int vectorCount) {}
