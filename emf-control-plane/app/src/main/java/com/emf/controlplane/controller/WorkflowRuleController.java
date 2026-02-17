package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.WorkflowRuleDto;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.service.WorkflowRuleService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/workflow-rules")
public class WorkflowRuleController {

    private final WorkflowRuleService workflowRuleService;

    public WorkflowRuleController(WorkflowRuleService workflowRuleService) {
        this.workflowRuleService = workflowRuleService;
    }

    @GetMapping
    public List<WorkflowRuleDto> listRules() {
        String tenantId = TenantContextHolder.requireTenantId();
        return workflowRuleService.listRules(tenantId).stream()
                .map(WorkflowRuleDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public WorkflowRuleDto getRule(@PathVariable String id) {
        return WorkflowRuleDto.fromEntity(workflowRuleService.getRule(id));
    }

    @PostMapping
    public ResponseEntity<WorkflowRuleDto> createRule(
            @RequestBody CreateWorkflowRuleRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var rule = workflowRuleService.createRule(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowRuleDto.fromEntity(rule));
    }

    @PutMapping("/{id}")
    public WorkflowRuleDto updateRule(
            @PathVariable String id,
            @RequestBody CreateWorkflowRuleRequest request) {
        return WorkflowRuleDto.fromEntity(workflowRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        workflowRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
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
}
