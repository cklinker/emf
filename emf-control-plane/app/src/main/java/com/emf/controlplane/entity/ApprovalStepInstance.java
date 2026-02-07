package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approval_step_instance")
public class ApprovalStepInstance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_instance_id", nullable = false)
    private ApprovalInstance approvalInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    private ApprovalStep step;

    @Column(name = "assigned_to", nullable = false, length = 36)
    private String assignedTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "acted_at")
    private Instant actedAt;

    public ApprovalStepInstance() { super(); }

    public ApprovalInstance getApprovalInstance() { return approvalInstance; }
    public void setApprovalInstance(ApprovalInstance approvalInstance) { this.approvalInstance = approvalInstance; }
    public ApprovalStep getStep() { return step; }
    public void setStep(ApprovalStep step) { this.step = step; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Instant getActedAt() { return actedAt; }
    public void setActedAt(Instant actedAt) { this.actedAt = actedAt; }
}
