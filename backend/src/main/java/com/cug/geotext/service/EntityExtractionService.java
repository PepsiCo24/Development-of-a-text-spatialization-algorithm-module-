package com.cug.geotext.service;

import com.cug.geotext.client.AiEntityResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.DocumentChunk;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EntityExtractionService {
    private final GeologicalDocumentMapper documentMapper;
    private final DocumentService documentService;
    private final DocumentChunkService chunkService;
    private final GeologicalEntityService entityService;
    private final RestClient aiRestClient;
    private final Executor executor;

    public EntityExtractionService(
        GeologicalDocumentMapper documentMapper, DocumentService documentService,
        DocumentChunkService chunkService, GeologicalEntityService entityService,
        RestClient aiRestClient, @Qualifier("parsingExecutor") Executor executor
    ) {
        this.documentMapper = documentMapper;
        this.documentService = documentService;
        this.chunkService = chunkService;
        this.entityService = entityService;
        this.aiRestClient = aiRestClient;
        this.executor = executor;
    }

    public ExtractionStatus start(long documentId, String provider) {
        GeologicalDocument document = documentService.get(documentId);
        if (!"PARSED".equals(document.getStatus())) throw new BusinessException(409, "请先完成文档解析");
        if ("EXTRACTING".equals(document.getEntityStatus())) throw new BusinessException(409, "实体识别正在进行中");
        document.setKnowledgeStatus("PENDING"); document.setKnowledgeProgress(0); document.setKnowledgeError(null);
        document.setAttributeCount(0); document.setRelationCount(0); document.setNormalizedCount(0); document.setKnowledgeExtractedAt(null);
        document.setSpatialStatus("PENDING"); document.setSpatialProgress(0); document.setSpatialError(null); document.setSpatialWarnings(null); document.setSpatialObjectCount(0); document.setSpatialExtractedAt(null);document.setGraphStatus("PENDING");document.setGraphProgress(0);document.setGraphError(null);document.setGraphNodeCount(0);document.setGraphRelationCount(0);document.setVectorChunkCount(0);document.setGraphSyncedAt(null);
        List<DocumentChunk> chunks = chunkService.list(documentId);
        if (chunks.isEmpty()) throw new BusinessException(409, "文档没有可识别的文本块");
        updateState(document, "EXTRACTING", 5, null);
        executor.execute(() -> extract(documentId, provider));
        return status(documentId);
    }

    public ExtractionStatus status(long documentId) {
        GeologicalDocument document = documentService.get(documentId);
        return new ExtractionStatus(documentId, defaultStatus(document.getEntityStatus()), zero(document.getEntityProgress()),
            document.getEntityError(), zero(document.getEntityCount()), document.getEntityExtractedAt());
    }

    public List<GeologicalEntity> entities(long documentId) {
        documentService.get(documentId);
        return entityService.list(documentId);
    }

    private void extract(long documentId, String provider) {
        GeologicalDocument document = null;
        try {
            document = documentService.get(documentId);
            List<DocumentChunk> chunks = chunkService.list(documentId);
            updateState(document, "EXTRACTING", 15, null);
            List<Map<String, Object>> requestChunks = chunks.stream().map(chunk -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("chunk_id", chunk.getId());
                item.put("content", chunk.getContent());
                item.put("page_start", chunk.getPageStart());
                item.put("page_end", chunk.getPageEnd());
                return item;
            }).toList();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("document_id", documentId);
            if (provider != null && !provider.isBlank()) request.put("provider", provider);
            request.put("chunks", requestChunks);
            AiEntityResponse response = aiRestClient.post().uri("/api/v1/entities/extract")
                .body(request).retrieve().body(AiEntityResponse.class);
            if (response == null || response.entities() == null) throw new IllegalStateException("AI 服务未返回实体结果");
            updateState(document, "EXTRACTING", 80, null);
            entityService.replace(documentId, response.provider(), response.model(), response.entities());
            document.setEntityStatus("COMPLETED");
            document.setEntityProgress(100);
            document.setEntityCount(response.entities().size());
            document.setEntityError(null);
            document.setEntityExtractedAt(OffsetDateTime.now());
            document.setUpdateTime(OffsetDateTime.now());
            documentMapper.updateById(document);
        } catch (Exception exception) {
            document = documentMapper.selectById(documentId);
            if (document != null) {
                String message = exception instanceof RestClientResponseException responseException
                    ? "AI 实体识别服务返回 HTTP " + responseException.getStatusCode().value()
                    : exception.getMessage();
                updateState(document, "FAILED", zero(document.getEntityProgress()), abbreviate(message));
            }
        }
    }

    private void updateState(GeologicalDocument document, String status, int progress, String error) {
        document.setEntityStatus(status);
        document.setEntityProgress(progress);
        document.setEntityError(error);
        document.setUpdateTime(OffsetDateTime.now());
        documentMapper.updateById(document);
    }

    private int zero(Integer value) { return value == null ? 0 : value; }
    private String defaultStatus(String value) { return value == null ? "PENDING" : value; }
    private String abbreviate(String value) {
        String message = value == null || value.isBlank() ? "地质实体识别失败" : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ExtractionStatus(long documentId, String status, int progress, String errorMessage,
                                   int entityCount, OffsetDateTime extractedAt) {}
}
