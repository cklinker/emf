package com.emf.controlplane.dto;

import java.util.List;

public class CreateApprovalProcessRequest {

    private String collectionId;
    private String name;
    private String description;
    private Boolean active;
    private String entryCriteria;
    private String recordEditability;
    private String initialSubmitterField;
    private String onSubmitFieldUpdates;
    private String onApprovalFieldUpdates;
    private String onRejectionFieldUpdates;
    private String onRecallFieldUpdates;
    private Boolean allowRecall;
    private Integer executionOrder;
    private List<StepRequest> steps;

    public static class StepRequest {
        private int stepNumber;
        private String name;
        private String description;
        private String entryCriteria;
        private String approverType;
        private String approverId;
        private String approverField;
        private Boolean unanimityRequired;
        private Integer escalationTimeoutHours;
        private String escalationAction;
        private String onApproveAction;
        private String onRejectAction;

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
        public Boolean getUnanimityRequired() { return unanimityRequired; }
        public void setUnanimityRequired(Boolean unanimityRequired) { this.unanimityRequired = unanimityRequired; }
        public Integer getEscalationTimeoutHours() { return escalationTimeoutHours; }
        public void setEscalationTimeoutHours(Integer escalationTimeoutHours) { this.escalationTimeoutHours = escalationTimeoutHours; }
        public String getEscalationAction() { return escalationAction; }
        public void setEscalationAction(String escalationAction) { this.escalationAction = escalationAction; }
        public String getOnApproveAction() { return onApproveAction; }
        public void setOnApproveAction(String onApproveAction) { this.onApproveAction = onApproveAction; }
        public String getOnRejectAction() { return onRejectAction; }
        public void setOnRejectAction(String onRejectAction) { this.onRejectAction = onRejectAction; }
    }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
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
    public Boolean getAllowRecall() { return allowRecall; }
    public void setAllowRecall(Boolean allowRecall) { this.allowRecall = allowRecall; }
    public Integer getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
    public List<StepRequest> getSteps() { return steps; }
    public void setSteps(List<StepRequest> steps) { this.steps = steps; }
}
