package com.emf.controlplane.dto;

import java.util.List;

public class CreateWorkflowRuleRequest {

    private String collectionId;
    private String name;
    private String description;
    private Boolean active;
    private String triggerType;
    private String filterFormula;
    private Boolean reEvaluateOnUpdate;
    private Integer executionOrder;
    private String errorHandling;
    private List<ActionRequest> actions;

    public static class ActionRequest {
        private String actionType;
        private Integer executionOrder;
        private String config;
        private Boolean active;

        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public Integer getExecutionOrder() { return executionOrder; }
        public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
        public String getConfig() { return config; }
        public void setConfig(String config) { this.config = config; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getFilterFormula() { return filterFormula; }
    public void setFilterFormula(String filterFormula) { this.filterFormula = filterFormula; }
    public Boolean getReEvaluateOnUpdate() { return reEvaluateOnUpdate; }
    public void setReEvaluateOnUpdate(Boolean reEvaluateOnUpdate) { this.reEvaluateOnUpdate = reEvaluateOnUpdate; }
    public Integer getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
    public String getErrorHandling() { return errorHandling; }
    public void setErrorHandling(String errorHandling) { this.errorHandling = errorHandling; }
    public List<ActionRequest> getActions() { return actions; }
    public void setActions(List<ActionRequest> actions) { this.actions = actions; }
}
