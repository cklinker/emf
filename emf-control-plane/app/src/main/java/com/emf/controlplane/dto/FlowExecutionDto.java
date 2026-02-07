package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FlowExecution;

import java.time.Instant;

public class FlowExecutionDto {

    private String id;
    private String flowId;
    private String flowName;
    private String status;
    private String startedBy;
    private String triggerRecordId;
    private String variables;
    private String currentNodeId;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;

    public static FlowExecutionDto fromEntity(FlowExecution entity) {
        FlowExecutionDto dto = new FlowExecutionDto();
        dto.setId(entity.getId());
        dto.setFlowId(entity.getFlow().getId());
        dto.setFlowName(entity.getFlow().getName());
        dto.setStatus(entity.getStatus());
        dto.setStartedBy(entity.getStartedBy());
        dto.setTriggerRecordId(entity.getTriggerRecordId());
        dto.setVariables(entity.getVariables());
        dto.setCurrentNodeId(entity.getCurrentNodeId());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public String getTriggerRecordId() { return triggerRecordId; }
    public void setTriggerRecordId(String triggerRecordId) { this.triggerRecordId = triggerRecordId; }
    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
