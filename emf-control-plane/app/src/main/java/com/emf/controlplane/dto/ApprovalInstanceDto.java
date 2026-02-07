package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ApprovalInstance;
import com.emf.controlplane.entity.ApprovalStepInstance;

import java.time.Instant;
import java.util.List;

public class ApprovalInstanceDto {

    private String id;
    private String approvalProcessId;
    private String approvalProcessName;
    private String collectionId;
    private String recordId;
    private String submittedBy;
    private int currentStepNumber;
    private String status;
    private Instant submittedAt;
    private Instant completedAt;
    private List<StepInstanceDto> stepInstances;

    public static ApprovalInstanceDto fromEntity(ApprovalInstance entity) {
        ApprovalInstanceDto dto = new ApprovalInstanceDto();
        dto.setId(entity.getId());
        dto.setApprovalProcessId(entity.getApprovalProcess().getId());
        dto.setApprovalProcessName(entity.getApprovalProcess().getName());
        dto.setCollectionId(entity.getCollectionId());
        dto.setRecordId(entity.getRecordId());
        dto.setSubmittedBy(entity.getSubmittedBy());
        dto.setCurrentStepNumber(entity.getCurrentStepNumber());
        dto.setStatus(entity.getStatus());
        dto.setSubmittedAt(entity.getSubmittedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setStepInstances(entity.getStepInstances().stream()
                .map(StepInstanceDto::fromEntity).toList());
        return dto;
    }

    public static class StepInstanceDto {
        private String id;
        private String stepId;
        private String assignedTo;
        private String status;
        private String comments;
        private Instant actedAt;

        public static StepInstanceDto fromEntity(ApprovalStepInstance entity) {
            StepInstanceDto dto = new StepInstanceDto();
            dto.setId(entity.getId());
            dto.setStepId(entity.getStep().getId());
            dto.setAssignedTo(entity.getAssignedTo());
            dto.setStatus(entity.getStatus());
            dto.setComments(entity.getComments());
            dto.setActedAt(entity.getActedAt());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStepId() { return stepId; }
        public void setStepId(String stepId) { this.stepId = stepId; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        public Instant getActedAt() { return actedAt; }
        public void setActedAt(Instant actedAt) { this.actedAt = actedAt; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApprovalProcessId() { return approvalProcessId; }
    public void setApprovalProcessId(String approvalProcessId) { this.approvalProcessId = approvalProcessId; }
    public String getApprovalProcessName() { return approvalProcessName; }
    public void setApprovalProcessName(String approvalProcessName) { this.approvalProcessName = approvalProcessName; }
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
    public List<StepInstanceDto> getStepInstances() { return stepInstances; }
    public void setStepInstances(List<StepInstanceDto> stepInstances) { this.stepInstances = stepInstances; }
}
