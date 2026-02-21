package com.emf.controlplane.controller;

import com.emf.controlplane.dto.ApprovalInstanceDto;
import com.emf.controlplane.dto.ApprovalProcessDto;
import com.emf.controlplane.dto.CreateApprovalProcessRequest;
import com.emf.controlplane.service.ApprovalService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    // --- Process CRUD ---

    @GetMapping("/processes")
    public List<ApprovalProcessDto> listProcesses() {
        String tenantId = TenantContextHolder.requireTenantId();
        return approvalService.listProcesses(tenantId).stream()
                .map(ApprovalProcessDto::fromEntity).toList();
    }

    @GetMapping("/processes/{id}")
    public ApprovalProcessDto getProcess(@PathVariable String id) {
        return ApprovalProcessDto.fromEntity(approvalService.getProcess(id));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_APPROVALS')")
    @PostMapping("/processes")
    public ResponseEntity<ApprovalProcessDto> createProcess(
            @RequestBody CreateApprovalProcessRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var process = approvalService.createProcess(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApprovalProcessDto.fromEntity(process));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_APPROVALS')")
    @PutMapping("/processes/{id}")
    public ApprovalProcessDto updateProcess(
            @PathVariable String id,
            @RequestBody CreateApprovalProcessRequest request) {
        return ApprovalProcessDto.fromEntity(approvalService.updateProcess(id, request));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_APPROVALS')")
    @DeleteMapping("/processes/{id}")
    public ResponseEntity<Void> deleteProcess(@PathVariable String id) {
        approvalService.deleteProcess(id);
        return ResponseEntity.noContent().build();
    }

    // --- Approval Instances ---

    @GetMapping("/instances")
    public List<ApprovalInstanceDto> listInstances() {
        String tenantId = TenantContextHolder.requireTenantId();
        return approvalService.listInstances(tenantId).stream()
                .map(ApprovalInstanceDto::fromEntity).toList();
    }

    @GetMapping("/instances/{id}")
    public ApprovalInstanceDto getInstance(@PathVariable String id) {
        return ApprovalInstanceDto.fromEntity(approvalService.getInstance(id));
    }

    @GetMapping("/instances/pending")
    public List<ApprovalInstanceDto> getPendingForUser(@RequestParam String userId) {
        return approvalService.getPendingForUser(userId).stream()
                .map(ApprovalInstanceDto::fromEntity).toList();
    }

    @PostMapping("/instances/submit")
    public ResponseEntity<ApprovalInstanceDto> submitForApproval(
            @RequestParam String collectionId,
            @RequestParam String recordId,
            @RequestParam String processId,
            @RequestParam String userId) {
        String tenantId = TenantContextHolder.requireTenantId();
        var instance = approvalService.submitForApproval(tenantId, collectionId, recordId, processId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApprovalInstanceDto.fromEntity(instance));
    }

    @PostMapping("/instances/steps/{stepInstanceId}/approve")
    public ApprovalInstanceDto approveStep(
            @PathVariable String stepInstanceId,
            @RequestParam String userId,
            @RequestParam(required = false) String comments) {
        var stepInstance = approvalService.approveStep(stepInstanceId, userId, comments);
        return ApprovalInstanceDto.fromEntity(stepInstance.getApprovalInstance());
    }

    @PostMapping("/instances/steps/{stepInstanceId}/reject")
    public ApprovalInstanceDto rejectStep(
            @PathVariable String stepInstanceId,
            @RequestParam String userId,
            @RequestParam(required = false) String comments) {
        var stepInstance = approvalService.rejectStep(stepInstanceId, userId, comments);
        return ApprovalInstanceDto.fromEntity(stepInstance.getApprovalInstance());
    }

    @PostMapping("/instances/{id}/recall")
    public ApprovalInstanceDto recallApproval(
            @PathVariable String id,
            @RequestParam String userId) {
        return ApprovalInstanceDto.fromEntity(approvalService.recallApproval(id, userId));
    }
}
