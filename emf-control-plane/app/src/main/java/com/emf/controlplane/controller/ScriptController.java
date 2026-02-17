package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateScriptRequest;
import com.emf.controlplane.dto.ScriptDto;
import com.emf.controlplane.dto.ScriptExecutionLogDto;
import com.emf.controlplane.service.ScriptService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @GetMapping
    public List<ScriptDto> listScripts() {
        String tenantId = TenantContextHolder.requireTenantId();
        return scriptService.listScripts(tenantId).stream()
                .map(ScriptDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public ScriptDto getScript(@PathVariable String id) {
        return ScriptDto.fromEntity(scriptService.getScript(id));
    }

    @PostMapping
    public ResponseEntity<ScriptDto> createScript(
            @RequestParam String userId,
            @RequestBody CreateScriptRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var script = scriptService.createScript(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScriptDto.fromEntity(script));
    }

    @PutMapping("/{id}")
    public ScriptDto updateScript(
            @PathVariable String id,
            @RequestBody CreateScriptRequest request) {
        return ScriptDto.fromEntity(scriptService.updateScript(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScript(@PathVariable String id) {
        scriptService.deleteScript(id);
        return ResponseEntity.noContent().build();
    }

    // --- Execution Logs ---

    @GetMapping("/logs")
    public List<ScriptExecutionLogDto> listLogs() {
        String tenantId = TenantContextHolder.requireTenantId();
        return scriptService.listExecutionLogs(tenantId).stream()
                .map(ScriptExecutionLogDto::fromEntity).toList();
    }

    @GetMapping("/{id}/logs")
    public List<ScriptExecutionLogDto> listLogsByScript(@PathVariable String id) {
        return scriptService.listExecutionLogsByScript(id).stream()
                .map(ScriptExecutionLogDto::fromEntity).toList();
    }
}
