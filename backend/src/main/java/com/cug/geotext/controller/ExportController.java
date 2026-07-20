package com.cug.geotext.controller;

import com.cug.geotext.service.ExportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/exports")
public class ExportController {
    private final ExportService service;public ExportController(ExportService service){this.service=service;}
    @GetMapping public ResponseEntity<byte[]>export(@RequestParam long documentId,@RequestParam String format,@RequestParam(defaultValue="entities")String dataset){ExportService.ExportFile file=service.export(documentId,format,dataset);String encoded=UriUtils.encode(file.filename(),StandardCharsets.UTF_8);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+encoded).body(file.content());}
}
