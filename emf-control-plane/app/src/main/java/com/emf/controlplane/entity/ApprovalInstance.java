package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "approval_instance")
public class ApprovalInstance extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_process_id", nullable = false)
    private ApprovalProcess approvalProcess;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "submitted_by", nullable = false, length = 36)
    private String submittedBy;

    @Column(name = "current_step_number", nullable = false)
    private int currentStepNumber = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "approvalInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApprovalStepInstance> stepInstances = new ArrayList<>();

    public ApprovalInstance() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public ApprovalProcess getApprovalProcess() { return approvalProcess; }
    public void setApprovalProcess(ApprovalProcess approvalProcess) { this.approvalProcess = approvalProcess; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public int getCurrentStepNumber() { return currentStepNumber; }
    public void setCurrentStepNumber(int currentStepNumber) { this.currentStepNumber = currentStepNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<ApprovalStepInstance> getStepInstances() { return stepInstances; }
    public void setStepInstances(List<ApprovalStepInstance> stepInstances) { this.stepInstances = stepInstances; }
}
