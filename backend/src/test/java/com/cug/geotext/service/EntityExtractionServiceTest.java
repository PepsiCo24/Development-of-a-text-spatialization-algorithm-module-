package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EntityExtractionServiceTest {
    @Test
    void sendsParsedChunksToAiAndPersistsEvidence() {
        GeologicalDocumentMapper documentMapper = mock(GeologicalDocumentMapper.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        GeologicalEntityService entityService = mock(GeologicalEntityService.class);
        GeologicalDocument document = new GeologicalDocument();
        document.setId(12L); document.setStatus("PARSED"); document.setEntityStatus("PENDING");
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(31L); chunk.setContent("燕山期花岗岩体侵入寒武系灰岩。"); chunk.setPageStart(3); chunk.setPageEnd(3);
        when(documentService.get(12L)).thenReturn(document);
        when(chunkService.list(12L)).thenReturn(List.of(chunk));

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8000/api/v1/entities/extract"))
            .andExpect(content().json("""
                {"document_id":12,"provider":"qwen","chunks":[{"chunk_id":31,"content":"燕山期花岗岩体侵入寒武系灰岩。","page_start":3,"page_end":3}]}
                """))
            .andRespond(withSuccess("""
                {"provider":"qwen","model":"qwen-plus","entities":[{"entity_name":"燕山期花岗岩体","entity_type":"ROCK_BODY","confidence":0.96,"source_text":"燕山期花岗岩体侵入寒武系灰岩。","page":3,"chunk_id":31,"source_start":0,"source_end":8}]}
                """, MediaType.APPLICATION_JSON));
        Executor direct = Runnable::run;
        EntityExtractionService service = new EntityExtractionService(
            documentMapper, documentService, chunkService, entityService, builder.build(), direct
        );

        EntityExtractionService.ExtractionStatus result = service.start(12L, "qwen");

        server.verify();
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.entityCount()).isEqualTo(1);
        verify(entityService).replace(eq(12L), eq("qwen"), eq("qwen-plus"), anyList());
        verify(documentMapper, atLeastOnce()).updateById(document);
    }
}
