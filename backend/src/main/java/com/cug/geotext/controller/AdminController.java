package com.cug.geotext.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.common.ApiResponse;
import com.cug.geotext.dto.*;
import com.cug.geotext.entity.*;
import com.cug.geotext.mapper.GeologicalDocumentMapper;
import com.cug.geotext.service.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserAdminService users;private final GeologicalDocumentMapper documents;private final SystemLogService logs;private final LlmConfigService configs;private final DemoDataService demoData;private final OneMapIntegrationService oneMap;public AdminController(UserAdminService users,GeologicalDocumentMapper documents,SystemLogService logs,LlmConfigService configs,DemoDataService demoData,OneMapIntegrationService oneMap){this.users=users;this.documents=documents;this.logs=logs;this.configs=configs;this.demoData=demoData;this.oneMap=oneMap;}
    @GetMapping("/users")public ApiResponse<List<AppUser>>users(){return ApiResponse.ok(users.list());}
    @PostMapping("/users")public ApiResponse<AppUser>create(@Valid@RequestBody UserRequest request){return ApiResponse.ok(users.create(request));}
    @PutMapping("/users/{id}")public ApiResponse<AppUser>update(@PathVariable long id,@Valid@RequestBody UserRequest request){return ApiResponse.ok(users.update(id,request));}
    @DeleteMapping("/users/{id}")public ApiResponse<Void>delete(@PathVariable long id){users.delete(id);return ApiResponse.ok(null);}
    @GetMapping("/tasks")public ApiResponse<List<GeologicalDocument>>tasks(){return ApiResponse.ok(documents.selectList(new LambdaQueryWrapper<GeologicalDocument>().orderByDesc(GeologicalDocument::getUpdateTime).last("LIMIT 500")));}
    @GetMapping("/logs")public ApiResponse<List<SystemLog>>logs(@RequestParam(required=false)String module){return ApiResponse.ok(logs.list(module));}
    @GetMapping("/llm-configs")public ApiResponse<List<LlmConfigService.ConfigView>>configs(){return ApiResponse.ok(configs.list());}
    @PutMapping("/llm-configs")public ApiResponse<LlmConfigService.ConfigView>save(@Valid@RequestBody LlmConfigRequest request){return ApiResponse.ok(configs.save(request));}
    @PostMapping("/llm-configs/{id}/apply")public ApiResponse<LlmConfigService.ConfigView>apply(@PathVariable long id){return ApiResponse.ok(configs.apply(id));}
    @PostMapping("/demo-data/restore")public ApiResponse<GeologicalDocument>restoreDemoData(Principal principal){return ApiResponse.ok(demoData.restore(principal.getName()));}
    @PostMapping("/one-map/push")public ApiResponse<OneMapIntegrationService.PushResult>pushToOneMap(@RequestParam long documentId){return ApiResponse.ok(oneMap.push(documentId));}
}
