package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateFlowRequest;
import com.emf.controlplane.dto.FlowDto;
import com.emf.controlplane.dto.FlowExecutionDto;
import com.emf.controlplane.service.FlowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/flows")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping
    public List<FlowDto> listFlows(@RequestParam String tenantId) {
        return flowService.listFlows(tenantId).stream()
                .map(FlowDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public FlowDto getFlow(@PathVariable String id) {
        return FlowDto.fromEntity(flowService.getFlow(id));
    }

    @PostMapping
    public ResponseEntity<FlowDto> createFlow(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody CreateFlowRequest request) {
        var flow = flowService.createFlow(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(FlowDto.fromEntity(flow));
    }

    @PutMapping("/{id}")
    public FlowDto updateFlow(
            @PathVariable String id,
            @RequestBody CreateFlowRequest request) {
        return FlowDto.fromEntity(flowService.updateFlow(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    // --- Executions ---

    @GetMapping("/executions")
    public List<FlowExecutionDto> listExecutions(@RequestParam String tenantId) {
        return flowService.listExecutions(tenantId).stream()
                .map(FlowExecutionDto::fromEntity).toList();
    }

    @GetMapping("/{id}/executions")
    public List<FlowExecutionDto> listExecutionsByFlow(@PathVariable String id) {
        return flowService.listExecutionsByFlow(id).stream()
                .map(FlowExecutionDto::fromEntity).toList();
    }

    @GetMapping("/executions/{executionId}")
    public FlowExecutionDto getExecution(@PathVariable String executionId) {
        return FlowExecutionDto.fromEntity(flowService.getExecution(executionId));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<FlowExecutionDto> startExecution(
            @PathVariable String id,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam(required = false) String triggerRecordId) {
        var execution = flowService.startExecution(tenantId, id, userId, triggerRecordId);
        return ResponseEntity.status(HttpStatus.CREATED).body(FlowExecutionDto.fromEntity(execution));
    }

    @PostMapping("/executions/{executionId}/cancel")
    public FlowExecutionDto cancelExecution(@PathVariable String executionId) {
        return FlowExecutionDto.fromEntity(flowService.cancelExecution(executionId));
    }
}
