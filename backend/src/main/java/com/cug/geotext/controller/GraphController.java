package com.cug.geotext.controller;

import com.cug.geotext.client.AiGraphView;
import com.cug.geotext.client.AiQuestionResponse;
import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.QuestionRequest;
import com.cug.geotext.service.GraphQueryService;
import com.cug.geotext.service.GraphSyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class GraphController {
    private final GraphSyncService syncService;private final GraphQueryService queryService;public GraphController(GraphSyncService syncService,GraphQueryService queryService){this.syncService=syncService;this.queryService=queryService;}
    @PostMapping("/api/documents/{id}/graph/sync") public ResponseEntity<ApiResponse<GraphSyncService.GraphStatus>>sync(@PathVariable long id){return ResponseEntity.accepted().body(ApiResponse.ok(syncService.start(id)));}
    @GetMapping("/api/documents/{id}/graph/status") public ApiResponse<GraphSyncService.GraphStatus>status(@PathVariable long id){return ApiResponse.ok(syncService.status(id));}
    @GetMapping("/api/graph/nodes") public ApiResponse<AiGraphView>nodes(@RequestParam(required=false)String query,@RequestParam(required=false)Integer limit,@RequestParam(required=false)Long documentId){return ApiResponse.ok(queryService.nodes(query,limit,documentId));}
    @GetMapping("/api/graph/nodes/{id}/expand") public ApiResponse<AiGraphView>expand(@PathVariable long id,@RequestParam(required=false)Integer depth){return ApiResponse.ok(queryService.expand(id,depth));}
    @GetMapping("/api/graph/path") public ApiResponse<AiGraphView>path(@RequestParam long sourceId,@RequestParam long targetId){return ApiResponse.ok(queryService.path(sourceId,targetId));}
    @PostMapping("/api/qa/ask") public ApiResponse<AiQuestionResponse>ask(@Valid@RequestBody QuestionRequest request){return ApiResponse.ok(queryService.ask(request));}
}
