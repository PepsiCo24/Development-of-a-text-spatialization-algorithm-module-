package com.cug.geotext.service;

import com.cug.geotext.common.BusinessException;
import com.cug.geotext.entity.GeologicalDocument;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OneMapIntegrationService {
    private final ExportService exports;
    private final DocumentService documents;
    private final RestClient.Builder restClient;
    private final String webhookUrl;

    public OneMapIntegrationService(ExportService exports, DocumentService documents, RestClient.Builder restClient,
            @Value("${app.one-map.webhook-url:}") String webhookUrl) {
        this.exports = exports; this.documents = documents; this.restClient = restClient; this.webhookUrl = webhookUrl;
    }

    public PushResult push(long documentId) {
        GeologicalDocument document = documents.get(documentId);
        ExportService.ExportFile payload = exports.export(documentId, "geojson", "spatial");
        if (payload.content().length == 0) throw new BusinessException(409, "No spatial result available");
        if (webhookUrl == null || webhookUrl.isBlank()) return new PushResult(documentId, document.getName(), "STAGED", "本地一张图原型已接收 GeoJSON；配置 ONE_MAP_WEBHOOK_URL 后可推送至外部汇聚平台。", payload.filename(), payload.content().length);
        restClient.build().post().uri(webhookUrl).contentType(MediaType.valueOf("application/geo+json")).body(payload.content()).retrieve().toBodilessEntity();
        return new PushResult(documentId, document.getName(), "PUSHED", "已推送至地球科学一张图汇聚接口。", payload.filename(), payload.content().length);
    }

    public record PushResult(long documentId, String documentName, String status, String message, String filename, int bytes) { }
}
