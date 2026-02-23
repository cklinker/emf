package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents a delayed workflow action that is pending execution.
 * <p>
 * Created by the DELAY action handler when a workflow contains a delay step.
 * A scheduled executor polls for pending actions whose {@code scheduledAt} time
 * has passed and resumes the workflow from the saved {@code actionIndex}.
 */
@Entity
@Table(name = "workflow_pending_action")
public class WorkflowPendingAction extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "execution_log_id", nullable = false, length = 36)
    private String executionLogId;

    @Column(name = "workflow_rule_id", nullable = false, length = 36)
    private String workflowRuleId;

    @Column(name = "action_index", nullable = false)
    private int actionIndex;

    @Column(name = "record_id", length = 36)
    private String recordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "record_snapshot", columnDefinition = "jsonb")
    private String recordSnapshot;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    public WorkflowPendingAction() {
        super();
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getExecutionLogId() { return executionLogId; }
    public void setExecutionLogId(String executionLogId) { this.executionLogId = executionLogId; }
    public String getWorkflowRuleId() { return workflowRuleId; }
    public void setWorkflowRuleId(String workflowRuleId) { this.workflowRuleId = workflowRuleId; }
    public int getActionIndex() { return actionIndex; }
    public void setActionIndex(int actionIndex) { this.actionIndex = actionIndex; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getRecordSnapshot() { return recordSnapshot; }
    public void setRecordSnapshot(String recordSnapshot) { this.recordSnapshot = recordSnapshot; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
