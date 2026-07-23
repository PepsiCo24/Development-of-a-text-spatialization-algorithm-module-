package com.cug.geotext.service;

import com.cug.geotext.client.AiGraphView;
import com.cug.geotext.client.AiQuestionResponse;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.QuestionRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GraphQueryService {
    private final RestClient client;
    private final LlmConfigService llmConfigService;

    public GraphQueryService(RestClient client, LlmConfigService llmConfigService) {
        this.client = client;
        this.llmConfigService = llmConfigService;
    }

    public AiGraphView nodes(String query, Integer limit, Long documentId) {
        if (documentId == null) return get("/api/v1/graph/nodes?query={query}&limit={limit}", query == null ? "" : query, limit == null ? 100 : limit);
        return get("/api/v1/graph/nodes?query={query}&limit={limit}&document_id={documentId}", query == null ? "" : query, limit == null ? 100 : limit, documentId);
    }

    public AiGraphView expand(long entityId, Integer depth) {
        return get("/api/v1/graph/expand/{id}?depth={depth}", entityId, depth == null ? 1 : depth);
    }

    public AiGraphView path(long sourceId, long targetId) {
        return get("/api/v1/graph/path?source_id={source}&target_id={target}", sourceId, targetId);
    }

    public AiQuestionResponse ask(QuestionRequest request) {
        llmConfigService.applyProvider(request.provider());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", request.question());
        if (request.provider() != null && !request.provider().isBlank()) body.put("provider", request.provider());
        body.put("limit", request.limit() == null ? 5 : request.limit());
        try {
            AiQuestionResponse response = client.post().uri("/api/v1/qa/ask").body(body).retrieve().body(AiQuestionResponse.class);
            if (response == null) throw new BusinessException(502, "问答服务未返回结果");
            return response;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(502, "智能问答服务调用失败: HTTP " + exception.getStatusCode().value() + detail(exception));
        }
    }

    private AiGraphView get(String uri, Object... variables) {
        try {
            AiGraphView response = client.get().uri(uri, variables).retrieve().body(AiGraphView.class);
            return response == null ? new AiGraphView(java.util.List.of(), java.util.List.of()) : response;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(502, "知识图谱服务调用失败: HTTP " + exception.getStatusCode().value() + detail(exception));
        }
    }

    private String detail(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        return body == null || body.isBlank() ? "" : ": " + body;
    }
}
