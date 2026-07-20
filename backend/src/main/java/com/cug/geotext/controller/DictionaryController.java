package com.cug.geotext.controller;

import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.DictionaryRequest;
import com.cug.geotext.entity.GeologicalDictionary;
import com.cug.geotext.service.DictionaryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {
    private final DictionaryService service;
    public DictionaryController(DictionaryService service){this.service=service;}
    @GetMapping public ApiResponse<List<GeologicalDictionary>> list(@RequestParam(required=false) String query,@RequestParam(required=false) String type){return ApiResponse.ok(service.list(query,type));}
    @PostMapping public ApiResponse<GeologicalDictionary> create(@Valid @RequestBody DictionaryRequest request){return ApiResponse.ok(service.create(request));}
    @PutMapping("/{id}") public ApiResponse<GeologicalDictionary> update(@PathVariable long id,@Valid @RequestBody DictionaryRequest request){return ApiResponse.ok(service.update(id,request));}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return ApiResponse.ok(null);}
}
