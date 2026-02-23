package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WorkflowActionLog;

import java.time.Instant;

public class WorkflowActionLogDto {

    private String id;
    private String executionLogId;
    private String actionId;
    private String actionType;
    private String status;
    private String errorMessage;
    private String inputSnapshot;
    private String outputSnapshot;
    private Integer durationMs;
    private Instant executedAt;

    public static WorkflowActionLogDto fromEntity(WorkflowActionLog entity) {
        WorkflowActionLogDto dto = new WorkflowActionLogDto();
        dto.setId(entity.getId());
        dto.setExecutionLogId(entity.getExecutionLogId());
        dto.setActionId(entity.getActionId());
        dto.setActionType(entity.getActionType());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setInputSnapshot(entity.getInputSnapshot());
        dto.setOutputSnapshot(entity.getOutputSnapshot());
        dto.setDurationMs(entity.getDurationMs());
        dto.setExecutedAt(entity.getExecutedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExecutionLogId() { return executionLogId; }
    public void setExecutionLogId(String executionLogId) { this.executionLogId = executionLogId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }
    public String getOutputSnapshot() { return outputSnapshot; }
    public void setOutputSnapshot(String outputSnapshot) { this.outputSnapshot = outputSnapshot; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
