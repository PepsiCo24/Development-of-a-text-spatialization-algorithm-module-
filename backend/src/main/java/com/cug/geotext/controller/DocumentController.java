package com.cug.geotext.controller;

import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.DocumentMetadata;
import com.cug.geotext.dto.DocumentQuery;
import com.cug.geotext.dto.DocumentStatusRequest;
import com.cug.geotext.dto.PageResult;
import com.cug.geotext.entity.GeologicalDocument;
import com.cug.geotext.service.DocumentService;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;
    public DocumentController(DocumentService documentService) { this.documentService = documentService; }

    @GetMapping
    public ApiResponse<PageResult<GeologicalDocument>> list(@Valid @ModelAttribute DocumentQuery query) {
        return ApiResponse.ok(documentService.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<GeologicalDocument> get(@PathVariable long id) { return ApiResponse.ok(documentService.get(id)); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GeologicalDocument> upload(
        @RequestPart("file") MultipartFile file,
        @Valid @ModelAttribute DocumentMetadata metadata,
        Principal principal
    ) {
        return ApiResponse.ok(documentService.upload(file, metadata, principal.getName()));
    }

    @PutMapping("/{id}")
    public ApiResponse<GeologicalDocument> update(@PathVariable long id, @Valid @RequestBody DocumentMetadata metadata) {
        return ApiResponse.ok(documentService.update(id, metadata));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<GeologicalDocument> updateStatus(@PathVariable long id, @Valid @RequestBody DocumentStatusRequest request) {
        return ApiResponse.ok(documentService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        documentService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable long id, @RequestParam(defaultValue = "inline") String disposition) {
        GeologicalDocument document = documentService.get(id);
        Resource resource = documentService.preview(id);
        String downloadName = document.getOriginalName() == null ? document.getName() : document.getOriginalName();
        String filename = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .header(HttpHeaders.CONTENT_DISPOSITION, mode + "; filename*=UTF-8''" + filename)
            .contentType(mediaType(document.getContentType()))
            .contentLength(document.getFileSize())
            .body(resource);
    }

    private MediaType mediaType(String contentType) {
        try { return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType); }
        catch (IllegalArgumentException exception) { return MediaType.APPLICATION_OCTET_STREAM; }
    }
}
