package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.ExecuteWorkflowRequest;
import com.emf.controlplane.dto.WorkflowActionLogDto;
import com.emf.controlplane.dto.WorkflowRuleDto;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.service.WorkflowRuleService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control/workflow-rules")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_WORKFLOWS')")
public class WorkflowRuleController {

    private final WorkflowRuleService workflowRuleService;

    public WorkflowRuleController(WorkflowRuleService workflowRuleService) {
        this.workflowRuleService = workflowRuleService;
    }

    @GetMapping
    public List<WorkflowRuleDto> listRules() {
        String tenantId = TenantContextHolder.requireTenantId();
        return workflowRuleService.listRuleDtos(tenantId);
    }

    @GetMapping("/{id}")
    public WorkflowRuleDto getRule(@PathVariable String id) {
        return workflowRuleService.getRuleDto(id);
    }

    @PostMapping
    public ResponseEntity<WorkflowRuleDto> createRule(
            @RequestBody CreateWorkflowRuleRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowRuleService.createRule(tenantId, request));
    }

    @PutMapping("/{id}")
    public WorkflowRuleDto updateRule(
            @PathVariable String id,
            @RequestBody CreateWorkflowRuleRequest request) {
        return workflowRuleService.updateRule(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        workflowRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    // --- Manual Execution ---

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeRule(
            @PathVariable String id,
            @RequestBody ExecuteWorkflowRequest request,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : "system";
        List<String> executionLogIds = workflowRuleService.executeManual(id, request, userId);
        return ResponseEntity.ok(Map.of("executionLogIds", executionLogIds));
    }

    // --- Execution Logs ---

    @GetMapping("/logs")
    public List<WorkflowExecutionLog> listLogs() {
        String tenantId = TenantContextHolder.requireTenantId();
        return workflowRuleService.listExecutionLogs(tenantId);
    }

    @GetMapping("/{id}/logs")
    public List<WorkflowExecutionLog> listLogsByRule(@PathVariable String id) {
        return workflowRuleService.listExecutionLogsByRule(id);
    }

    // --- Action Logs ---

    @GetMapping("/logs/{executionLogId}/actions")
    public List<WorkflowActionLogDto> listActionLogs(@PathVariable String executionLogId) {
        return workflowRuleService.listActionLogsByExecution(executionLogId);
    }
}
