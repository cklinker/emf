package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WorkflowRuleVersion;

import java.time.Instant;

/**
 * DTO for workflow rule version history entries.
 */
public class WorkflowRuleVersionDto {

    private String id;
    private String workflowRuleId;
    private int versionNumber;
    private String snapshot;
    private String changeSummary;
    private String createdBy;
    private Instant createdAt;

    public static WorkflowRuleVersionDto fromEntity(WorkflowRuleVersion entity) {
        WorkflowRuleVersionDto dto = new WorkflowRuleVersionDto();
        dto.setId(entity.getId());
        dto.setWorkflowRuleId(entity.getWorkflowRuleId());
        dto.setVersionNumber(entity.getVersionNumber());
        dto.setSnapshot(entity.getSnapshot());
        dto.setChangeSummary(entity.getChangeSummary());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
