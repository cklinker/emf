package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "script_execution_log")
public class ScriptExecutionLog extends TenantScopedEntity {

    @Column(name = "script_id", nullable = false, length = 36)
    private String scriptId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "cpu_ms")
    private Integer cpuMs;

    @Column(name = "queries_executed")
    private Integer queriesExecuted;

    @Column(name = "dml_rows")
    private Integer dmlRows;

    @Column(name = "callouts")
    private Integer callouts;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "log_output", columnDefinition = "TEXT")
    private String logOutput;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    public ScriptExecutionLog() { super(); }

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
}
