package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ScriptExecutionLog;

import java.time.Instant;

public class ScriptExecutionLogDto {

    private String id;
    private String tenantId;
    private String scriptId;
    private String status;
    private String triggerType;
    private String recordId;
    private Integer durationMs;
    private Integer cpuMs;
    private Integer queriesExecuted;
    private Integer dmlRows;
    private Integer callouts;
    private String errorMessage;
    private String logOutput;
    private Instant executedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScriptExecutionLogDto fromEntity(ScriptExecutionLog entity) {
        ScriptExecutionLogDto dto = new ScriptExecutionLogDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setScriptId(entity.getScriptId());
        dto.setStatus(entity.getStatus());
        dto.setTriggerType(entity.getTriggerType());
        dto.setRecordId(entity.getRecordId());
        dto.setDurationMs(entity.getDurationMs());
        dto.setCpuMs(entity.getCpuMs());
        dto.setQueriesExecuted(entity.getQueriesExecuted());
        dto.setDmlRows(entity.getDmlRows());
        dto.setCallouts(entity.getCallouts());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setLogOutput(entity.getLogOutput());
        dto.setExecutedAt(entity.getExecutedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Integer getCpuMs() { return cpuMs; }
    public void setCpuMs(Integer cpuMs) { this.cpuMs = cpuMs; }
    public Integer getQueriesExecuted() { return queriesExecuted; }
    public void setQueriesExecuted(Integer queriesExecuted) { this.queriesExecuted = queriesExecuted; }
    public Integer getDmlRows() { return dmlRows; }
    public void setDmlRows(Integer dmlRows) { this.dmlRows = dmlRows; }
    public Integer getCallouts() { return callouts; }
    public void setCallouts(Integer callouts) { this.callouts = callouts; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getLogOutput() { return logOutput; }
    public void setLogOutput(String logOutput) { this.logOutput = logOutput; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
