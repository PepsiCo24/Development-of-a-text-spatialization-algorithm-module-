package com.cug.geotext.controller;

import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.service.AnalysisReportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/reports")
public class AnalysisReportController {
    private final AnalysisReportService service;
    public AnalysisReportController(AnalysisReportService service) { this.service = service; }

    @GetMapping("/{documentId}")
    public ApiResponse<AnalysisReportService.ReportView> preview(@PathVariable long documentId) { return ApiResponse.ok(service.report(documentId)); }

    @GetMapping("/{documentId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long documentId) {
        AnalysisReportService.ReportFile file = service.pdf(documentId);
        String filename = UriUtils.encode(file.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(file.content());
    }
}
