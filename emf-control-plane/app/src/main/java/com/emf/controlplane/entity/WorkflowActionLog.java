package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "workflow_action_log")
public class WorkflowActionLog extends BaseEntity {

    @Column(name = "execution_log_id", nullable = false, length = 36)
    private String executionLogId;

    @Column(name = "action_id", length = 36)
    private String actionId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", columnDefinition = "jsonb")
    private String outputSnapshot;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "attempt_number")
    private int attemptNumber = 1;

    public WorkflowActionLog() {
        super();
        this.executedAt = Instant.now();
    }

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
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
}
