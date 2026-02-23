package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowRule;

import java.time.Instant;
import java.util.List;

public class WorkflowRuleDto {

    private String id;
    private String collectionId;
    private String name;
    private String description;
    private boolean active;
    private String triggerType;
    private String filterFormula;
    private boolean reEvaluateOnUpdate;
    private int executionOrder;
    private String errorHandling;
    private List<ActionDto> actions;
    private Instant createdAt;
    private Instant updatedAt;

    public static WorkflowRuleDto fromEntity(WorkflowRule entity) {
        WorkflowRuleDto dto = new WorkflowRuleDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.isActive());
        dto.setTriggerType(entity.getTriggerType());
        dto.setFilterFormula(entity.getFilterFormula());
        dto.setReEvaluateOnUpdate(entity.isReEvaluateOnUpdate());
        dto.setExecutionOrder(entity.getExecutionOrder());
        dto.setErrorHandling(entity.getErrorHandling());
        dto.setActions(entity.getActions().stream().map(ActionDto::fromEntity).toList());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public static class ActionDto {
        private String id;
        private String actionType;
        private int executionOrder;
        private String config;
        private boolean active;

        public static ActionDto fromEntity(WorkflowAction entity) {
            ActionDto dto = new ActionDto();
            dto.setId(entity.getId());
            dto.setActionType(entity.getActionType());
            dto.setExecutionOrder(entity.getExecutionOrder());
            dto.setConfig(entity.getConfig());
            dto.setActive(entity.isActive());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public int getExecutionOrder() { return executionOrder; }
        public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
        public String getConfig() { return config; }
        public void setConfig(String config) { this.config = config; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
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
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getFilterFormula() { return filterFormula; }
    public void setFilterFormula(String filterFormula) { this.filterFormula = filterFormula; }
    public boolean isReEvaluateOnUpdate() { return reEvaluateOnUpdate; }
    public void setReEvaluateOnUpdate(boolean reEvaluateOnUpdate) { this.reEvaluateOnUpdate = reEvaluateOnUpdate; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public String getErrorHandling() { return errorHandling; }
    public void setErrorHandling(String errorHandling) { this.errorHandling = errorHandling; }
    public List<ActionDto> getActions() { return actions; }
    public void setActions(List<ActionDto> actions) { this.actions = actions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
