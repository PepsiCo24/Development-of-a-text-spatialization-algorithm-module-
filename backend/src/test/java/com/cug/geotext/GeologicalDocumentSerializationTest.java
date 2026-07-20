package com.cug.geotext;

import static org.assertj.core.api.Assertions.assertThat;

import com.cug.geotext.entity.GeologicalDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeologicalDocumentSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiSerializationDoesNotExposeServerStoragePathOrCreatorId() throws Exception {
        GeologicalDocument document = new GeologicalDocument();
        document.setId(7L);
        document.setName("区域地质报告.pdf");
        document.setFilePath("2026/07/internal-id.pdf");
        document.setCreatedBy(12L);
        document.setOriginalName("区域地质报告.pdf");

        String json = objectMapper.writeValueAsString(document);

        assertThat(json).contains("区域地质报告.pdf");
        assertThat(json).doesNotContain("filePath", "internal-id", "createdBy");
    }
}

