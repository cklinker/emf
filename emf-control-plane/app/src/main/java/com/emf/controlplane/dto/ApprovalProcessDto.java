package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ApprovalProcess;
import com.emf.controlplane.entity.ApprovalStep;

import java.time.Instant;
import java.util.List;

public class ApprovalProcessDto {

    private String id;
    private String collectionId;
    private String name;
    private String description;
    private boolean active;
    private String entryCriteria;
    private String recordEditability;
    private String initialSubmitterField;
    private String onSubmitFieldUpdates;
    private String onApprovalFieldUpdates;
    private String onRejectionFieldUpdates;
    private String onRecallFieldUpdates;
    private boolean allowRecall;
    private int executionOrder;
    private List<StepDto> steps;
    private Instant createdAt;
    private Instant updatedAt;

    public static ApprovalProcessDto fromEntity(ApprovalProcess entity) {
        ApprovalProcessDto dto = new ApprovalProcessDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.isActive());
        dto.setEntryCriteria(entity.getEntryCriteria());
        dto.setRecordEditability(entity.getRecordEditability());
        dto.setInitialSubmitterField(entity.getInitialSubmitterField());
        dto.setOnSubmitFieldUpdates(entity.getOnSubmitFieldUpdates());
        dto.setOnApprovalFieldUpdates(entity.getOnApprovalFieldUpdates());
        dto.setOnRejectionFieldUpdates(entity.getOnRejectionFieldUpdates());
        dto.setOnRecallFieldUpdates(entity.getOnRecallFieldUpdates());
        dto.setAllowRecall(entity.isAllowRecall());
        dto.setExecutionOrder(entity.getExecutionOrder());
        dto.setSteps(entity.getSteps().stream().map(StepDto::fromEntity).toList());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public static class StepDto {
        private String id;
        private int stepNumber;
        private String name;
        private String description;
        private String entryCriteria;
        private String approverType;
        private String approverId;
        private String approverField;
        private boolean unanimityRequired;
        private Integer escalationTimeoutHours;
        private String escalationAction;
        private String onApproveAction;
        private String onRejectAction;

        public static StepDto fromEntity(ApprovalStep entity) {
            StepDto dto = new StepDto();
            dto.setId(entity.getId());
            dto.setStepNumber(entity.getStepNumber());
            dto.setName(entity.getName());
            dto.setDescription(entity.getDescription());
            dto.setEntryCriteria(entity.getEntryCriteria());
            dto.setApproverType(entity.getApproverType());
            dto.setApproverId(entity.getApproverId());
            dto.setApproverField(entity.getApproverField());
            dto.setUnanimityRequired(entity.isUnanimityRequired());
            dto.setEscalationTimeoutHours(entity.getEscalationTimeoutHours());
            dto.setEscalationAction(entity.getEscalationAction());
            dto.setOnApproveAction(entity.getOnApproveAction());
            dto.setOnRejectAction(entity.getOnRejectAction());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getEntryCriteria() { return entryCriteria; }
    public void setEntryCriteria(String entryCriteria) { this.entryCriteria = entryCriteria; }
    public String getRecordEditability() { return recordEditability; }
    public void setRecordEditability(String recordEditability) { this.recordEditability = recordEditability; }
    public String getInitialSubmitterField() { return initialSubmitterField; }
    public void setInitialSubmitterField(String initialSubmitterField) { this.initialSubmitterField = initialSubmitterField; }
    public String getOnSubmitFieldUpdates() { return onSubmitFieldUpdates; }
    public void setOnSubmitFieldUpdates(String onSubmitFieldUpdates) { this.onSubmitFieldUpdates = onSubmitFieldUpdates; }
    public String getOnApprovalFieldUpdates() { return onApprovalFieldUpdates; }
    public void setOnApprovalFieldUpdates(String onApprovalFieldUpdates) { this.onApprovalFieldUpdates = onApprovalFieldUpdates; }
    public String getOnRejectionFieldUpdates() { return onRejectionFieldUpdates; }
    public void setOnRejectionFieldUpdates(String onRejectionFieldUpdates) { this.onRejectionFieldUpdates = onRejectionFieldUpdates; }
    public String getOnRecallFieldUpdates() { return onRecallFieldUpdates; }
    public void setOnRecallFieldUpdates(String onRecallFieldUpdates) { this.onRecallFieldUpdates = onRecallFieldUpdates; }
    public boolean isAllowRecall() { return allowRecall; }
    public void setAllowRecall(boolean allowRecall) { this.allowRecall = allowRecall; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public List<StepDto> getSteps() { return steps; }
    public void setSteps(List<StepDto> steps) { this.steps = steps; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
