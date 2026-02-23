package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Stores version snapshots of workflow rules.
 * <p>
 * Each time a workflow rule is updated, a new version is created with a serialized
 * snapshot of the complete rule + actions. This enables version history viewing,
 * diff comparison, and rollback.
 */
@Entity
@Table(name = "workflow_rule_version")
public class WorkflowRuleVersion extends BaseEntity {

    @Column(name = "workflow_rule_id", nullable = false, length = 36)
    private String workflowRuleId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    public WorkflowRuleVersion() { super(); }

    public String getWorkflowRuleId() { return workflowRuleId; }
    public void setWorkflowRuleId(String workflowRuleId) { this.workflowRuleId = workflowRuleId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
