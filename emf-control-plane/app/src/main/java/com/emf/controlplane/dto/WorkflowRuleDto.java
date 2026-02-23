package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private List<String> triggerFields;
    private String cronExpression;
    private String timezone;
    private Instant lastScheduledRun;
    private String executionMode;
    private List<ActionDto> actions;
    private Instant createdAt;
    private Instant updatedAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        dto.setTriggerFields(parseTriggerFields(entity.getTriggerFields()));
        dto.setCronExpression(entity.getCronExpression());
        dto.setTimezone(entity.getTimezone());
        dto.setLastScheduledRun(entity.getLastScheduledRun());
        dto.setExecutionMode(entity.getExecutionMode());
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
        private int retryCount;
        private int retryDelaySeconds;
        private String retryBackoff;

        public static ActionDto fromEntity(WorkflowAction entity) {
            ActionDto dto = new ActionDto();
            dto.setId(entity.getId());
            dto.setActionType(entity.getActionType());
            dto.setExecutionOrder(entity.getExecutionOrder());
            dto.setConfig(entity.getConfig());
            dto.setActive(entity.isActive());
            dto.setRetryCount(entity.getRetryCount());
            dto.setRetryDelaySeconds(entity.getRetryDelaySeconds());
            dto.setRetryBackoff(entity.getRetryBackoff());
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
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public int getRetryDelaySeconds() { return retryDelaySeconds; }
        public void setRetryDelaySeconds(int retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
        public String getRetryBackoff() { return retryBackoff; }
        public void setRetryBackoff(String retryBackoff) { this.retryBackoff = retryBackoff; }
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
    public List<String> getTriggerFields() { return triggerFields; }
    public void setTriggerFields(List<String> triggerFields) { this.triggerFields = triggerFields; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getLastScheduledRun() { return lastScheduledRun; }
    public void setLastScheduledRun(Instant lastScheduledRun) { this.lastScheduledRun = lastScheduledRun; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public List<ActionDto> getActions() { return actions; }
    public void setActions(List<ActionDto> actions) { this.actions = actions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Parses a JSONB trigger_fields string (e.g. '["status","priority"]') to a List.
     */
    public static List<String> parseTriggerFields(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serializes a List of trigger fields to a JSONB string.
     */
    public static String serializeTriggerFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            return null;
        }
    }
}
