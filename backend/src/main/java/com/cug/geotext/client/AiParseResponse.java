package com.cug.geotext.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiParseResponse(
    String filename,
    @JsonProperty("document_type") String documentType,
    @JsonProperty("page_count") Integer pageCount,
    List<AiChunk> chunks,
    List<String> warnings
) {
    public record AiChunk(
        @JsonProperty("chunk_index") Integer chunkIndex,
        @JsonProperty("chapter_title") String chapterTitle,
        String content,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd,
        @JsonProperty("char_count") Integer charCount
    ) {}
}

