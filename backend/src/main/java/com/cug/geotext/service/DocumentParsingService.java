package com.cug.geotext.service;

import com.cug.geotext.client.AiParseResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import com.cug.geotext.storage.FileStorageService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DocumentParsingService {
    private final GeologicalDocumentMapper documentMapper;
    private final DocumentService documentService;
    private final DocumentChunkService chunkService;
    private final FileStorageService storage;
    private final RestClient aiRestClient;
    private final Executor executor;

    public DocumentParsingService(
        GeologicalDocumentMapper documentMapper,
        DocumentService documentService,
        DocumentChunkService chunkService,
        FileStorageService storage,
        RestClient aiRestClient,
        @Qualifier("parsingExecutor") Executor executor
    ) {
        this.documentMapper = documentMapper;
        this.documentService = documentService;
        this.chunkService = chunkService;
        this.storage = storage;
        this.aiRestClient = aiRestClient;
        this.executor = executor;
    }

    public ParseStatus start(long documentId) {
        GeologicalDocument document = documentService.get(documentId);
        if ("PARSING".equals(document.getStatus())) throw new BusinessException(409, "资料正在解析中");
        updateState(document, "PARSING", 5, null);
        executor.execute(() -> parse(documentId));
        return status(documentId);
    }

    public ParseStatus status(long documentId) {
        GeologicalDocument document = documentService.get(documentId);
        return new ParseStatus(document.getId(), document.getStatus(), valueOrZero(document.getParseProgress()),
            document.getErrorMessage(), valueOrZero(document.getPageCount()), valueOrZero(document.getChunkCount()), document.getParsedAt());
    }

    public List<DocumentChunk> chunks(long documentId) {
        documentService.get(documentId);
        return chunkService.list(documentId);
    }

    private void parse(long documentId) {
        GeologicalDocument document;
        try {
            document = documentService.get(documentId);
            updateState(document, "PARSING", 15, null);
            Resource resource = storage.load(document.getFilePath());
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentDispositionFormData("file", document.getOriginalName());
            fileHeaders.setContentType(safeMediaType(document.getContentType()));
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(resource, fileHeaders));
            AiParseResponse response = aiRestClient.post().uri("/api/v1/documents/parse")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(AiParseResponse.class);
            if (response == null || response.chunks() == null || response.chunks().isEmpty()) {
                throw new IllegalStateException("AI 解析服务未返回有效文本分块");
            }
            updateState(document, "PARSING", 75, null);
            chunkService.replace(documentId, response.chunks());
            document.setStatus("PARSED");
            document.setParseProgress(100);
            document.setPageCount(response.pageCount());
            document.setChunkCount(response.chunks().size());
            document.setErrorMessage(null);
            document.setParsedAt(OffsetDateTime.now());
            document.setUpdateTime(OffsetDateTime.now());
            documentMapper.updateById(document);
        } catch (Exception exception) {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                String message = exception instanceof RestClientResponseException responseException
                    ? "AI 解析服务返回 HTTP " + responseException.getStatusCode().value()
                    : exception.getMessage();
                updateState(document, "FAILED", valueOrZero(document.getParseProgress()), abbreviate(message));
            }
        }
    }

    private void updateState(GeologicalDocument document, String status, int progress, String error) {
        document.setStatus(status);
        document.setParseProgress(progress);
        document.setErrorMessage(error);
        document.setUpdateTime(OffsetDateTime.now());
        documentMapper.updateById(document);
    }

    private MediaType safeMediaType(String value) {
        try { return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value); }
        catch (IllegalArgumentException exception) { return MediaType.APPLICATION_OCTET_STREAM; }
    }

    private int valueOrZero(Integer value) { return value == null ? 0 : value; }
    private String abbreviate(String value) {
        String message = value == null || value.isBlank() ? "文档解析失败" : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ParseStatus(Long documentId, String status, int progress, String errorMessage, int pageCount,
                              int chunkCount, OffsetDateTime parsedAt) {}
}
