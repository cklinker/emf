package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "approval_step")
public class ApprovalStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_process_id", nullable = false)
    private ApprovalProcess approvalProcess;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "entry_criteria", columnDefinition = "TEXT")
    private String entryCriteria;

    @Column(name = "approver_type", nullable = false, length = 30)
    private String approverType;

    @Column(name = "approver_id", length = 36)
    private String approverId;

    @Column(name = "approver_field", length = 100)
    private String approverField;

    @Column(name = "unanimity_required")
    private boolean unanimityRequired = false;

    @Column(name = "escalation_timeout_hours")
    private Integer escalationTimeoutHours;

    @Column(name = "escalation_action", length = 20)
    private String escalationAction;

    @Column(name = "on_approve_action", length = 20)
    private String onApproveAction = "NEXT_STEP";

    @Column(name = "on_reject_action", length = 20)
    private String onRejectAction = "REJECT_FINAL";

    public ApprovalStep() { super(); }

    public ApprovalProcess getApprovalProcess() { return approvalProcess; }
    public void setApprovalProcess(ApprovalProcess approvalProcess) { this.approvalProcess = approvalProcess; }
    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEntryCriteria() { return entryCriteria; }
    public void setEntryCriteria(String entryCriteria) { this.entryCriteria = entryCriteria; }
    public String getApproverType() { return approverType; }
    public void setApproverType(String approverType) { this.approverType = approverType; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public String getApproverField() { return approverField; }
    public void setApproverField(String approverField) { this.approverField = approverField; }
    public boolean isUnanimityRequired() { return unanimityRequired; }
    public void setUnanimityRequired(boolean unanimityRequired) { this.unanimityRequired = unanimityRequired; }
    public Integer getEscalationTimeoutHours() { return escalationTimeoutHours; }
    public void setEscalationTimeoutHours(Integer escalationTimeoutHours) { this.escalationTimeoutHours = escalationTimeoutHours; }
    public String getEscalationAction() { return escalationAction; }
    public void setEscalationAction(String escalationAction) { this.escalationAction = escalationAction; }
    public String getOnApproveAction() { return onApproveAction; }
    public void setOnApproveAction(String onApproveAction) { this.onApproveAction = onApproveAction; }
    public String getOnRejectAction() { return onRejectAction; }
    public void setOnRejectAction(String onRejectAction) { this.onRejectAction = onRejectAction; }
}
