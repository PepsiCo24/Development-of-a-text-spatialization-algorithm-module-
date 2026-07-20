package com.cug.geotext.controller;

import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.EntityExtractionRequest;
import com.cug.geotext.entity.SpatialObject;
import com.cug.geotext.service.SpatialExtractionService;
import com.cug.geotext.service.SpatialObjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SpatialController {
    private final SpatialExtractionService extractionService;private final SpatialObjectService objectService;
    public SpatialController(SpatialExtractionService extractionService,SpatialObjectService objectService){this.extractionService=extractionService;this.objectService=objectService;}
    @PostMapping("/api/documents/{id}/spatial/extract") public ResponseEntity<ApiResponse<SpatialExtractionService.SpatialStatus>>extract(@PathVariable long id,@Valid@RequestBody EntityExtractionRequest request){return ResponseEntity.accepted().body(ApiResponse.ok(extractionService.start(id,request.provider())));}
    @GetMapping("/api/documents/{id}/spatial/status") public ApiResponse<SpatialExtractionService.SpatialStatus>status(@PathVariable long id){return ApiResponse.ok(extractionService.status(id));}
    @GetMapping("/api/spatial-objects") public ApiResponse<List<SpatialObject>>list(@RequestParam(required=false)Long documentId){return ApiResponse.ok(objectService.list(documentId));}
    @GetMapping("/api/spatial-objects/{id}") public ApiResponse<SpatialObject>get(@PathVariable long id){return ApiResponse.ok(objectService.get(id));}
}
