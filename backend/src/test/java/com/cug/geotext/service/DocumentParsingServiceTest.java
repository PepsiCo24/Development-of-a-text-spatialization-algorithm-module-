package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cug.geotext.client.AiParseResponse;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import com.cug.geotext.storage.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DocumentParsingServiceTest {
    @TempDir Path tempDirectory;

    @Test
    void sendsStoredFileToAiServiceAndPersistsReturnedChunks() {
        GeologicalDocumentMapper documentMapper = mock(GeologicalDocumentMapper.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        FileStorageService storage = new FileStorageService(tempDirectory.toString());
        var stored = storage.store(new MockMultipartFile("file", "survey.txt", "text/plain", "granite".getBytes(StandardCharsets.UTF_8)));

        GeologicalDocument document = new GeologicalDocument();
        document.setId(9L);
        document.setName("survey.txt");
        document.setOriginalName("survey.txt");
        document.setContentType("text/plain");
        document.setFilePath(stored.relativePath());
        document.setStatus("UPLOADED");
        when(documentService.get(9L)).thenReturn(document);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8000/api/v1/documents/parse"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"filename":"survey.txt","document_type":"TXT","page_count":1,"warnings":[],"chunks":[
                  {"chunk_index":0,"chapter_title":"第一章 地质","content":"花岗岩体。","page_start":1,"page_end":1,"char_count":5}
                ]}
                """, MediaType.APPLICATION_JSON));
        Executor directExecutor = Runnable::run;
        DocumentParsingService service = new DocumentParsingService(
            documentMapper, documentService, chunkService, storage, builder.build(), directExecutor
        );

        DocumentParsingService.ParseStatus result = service.start(9L);

        server.verify();
        assertThat(result.status()).isEqualTo("PARSED");
        assertThat(result.progress()).isEqualTo(100);
        assertThat(document.getPageCount()).isEqualTo(1);
        assertThat(document.getChunkCount()).isEqualTo(1);
        verify(chunkService).replace(eq(9L), org.mockito.ArgumentMatchers.<List<AiParseResponse.AiChunk>>any());
        verify(documentMapper, atLeastOnce()).updateById(document);
    }
}
