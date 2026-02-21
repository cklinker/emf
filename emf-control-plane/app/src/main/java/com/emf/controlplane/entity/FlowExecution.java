package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "flow_execution")
public class FlowExecution extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private Flow flow;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "started_by", length = 36)
    private String startedBy;

    @Column(name = "trigger_record_id", length = 36)
    private String triggerRecordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables = "{}";

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public FlowExecution() { super(); }

    public Flow getFlow() { return flow; }
    public void setFlow(Flow flow) { this.flow = flow; }
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
