package com.cug.geotext.controller;

import com.cug.geotext.client.AiGraphView;
import com.cug.geotext.client.AiQuestionResponse;
import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.QuestionRequest;
import com.cug.geotext.service.GraphQueryService;
import com.cug.geotext.service.GraphSyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class GraphController {
    private final GraphSyncService syncService;private final GraphQueryService queryService;public GraphController(GraphSyncService syncService,GraphQueryService queryService){this.syncService=syncService;this.queryService=queryService;}
    @PostMapping("/api/documents/{id}/graph/sync") public ResponseEntity<ApiResponse<GraphSyncService.GraphStatus>>sync(@PathVariable long id){return ResponseEntity.accepted().body(ApiResponse.ok(syncService.start(id)));}
    @GetMapping("/api/documents/{id}/graph/status") public ApiResponse<GraphSyncService.GraphStatus>status(@PathVariable long id){return ApiResponse.ok(syncService.status(id));}
    @GetMapping("/api/graph/nodes") public ApiResponse<AiGraphView>nodes(@RequestParam(required=false)String query,@RequestParam(required=false)Integer limit,@RequestParam(required=false)Long documentId){return ApiResponse.ok(queryService.nodes(query,limit,documentId));}
    @GetMapping("/api/graph/nodes/{id}/expand") public ApiResponse<AiGraphView>expand(@PathVariable long id,@RequestParam(required=false)Integer depth){return ApiResponse.ok(queryService.expand(id,depth));}
    @GetMapping("/api/graph/path") public ApiResponse<AiGraphView>path(@RequestParam long sourceId,@RequestParam long targetId){return ApiResponse.ok(queryService.path(sourceId,targetId));}
    @PostMapping("/api/qa/ask") public ApiResponse<AiQuestionResponse>ask(@Valid@RequestBody QuestionRequest request){return ApiResponse.ok(queryService.ask(request));}
    @PostMapping(value="/api/qa/ask/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public ResponseEntity<StreamingResponseBody>askStream(@Valid@RequestBody QuestionRequest request){return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).header("Cache-Control","no-cache").header("X-Accel-Buffering","no").body(queryService.askStream(request));}
}
