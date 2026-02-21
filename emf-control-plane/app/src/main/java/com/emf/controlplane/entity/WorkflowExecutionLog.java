package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_execution_log")
public class WorkflowExecutionLog extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_rule_id", nullable = false)
    private WorkflowRule workflowRule;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "actions_executed")
    private int actionsExecuted = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    public WorkflowExecutionLog() { super(); }

    public WorkflowRule getWorkflowRule() { return workflowRule; }
    public void setWorkflowRule(WorkflowRule workflowRule) { this.workflowRule = workflowRule; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getActionsExecuted() { return actionsExecuted; }
    public void setActionsExecuted(int actionsExecuted) { this.actionsExecuted = actionsExecuted; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
}
