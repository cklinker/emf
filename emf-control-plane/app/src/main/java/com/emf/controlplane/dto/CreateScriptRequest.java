package com.emf.controlplane.dto;

import java.util.List;

public class CreateScriptRequest {

    private String name;
    private String description;
    private String scriptType;
    private String language;
    private String sourceCode;
    private Boolean active;
    private List<TriggerRequest> triggers;

    public static class TriggerRequest {
        private String collectionId;
        private String triggerEvent;
        private Integer executionOrder;
        private Boolean active;

        public String getCollectionId() { return collectionId; }
        public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
        public String getTriggerEvent() { return triggerEvent; }
        public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
        public Integer getExecutionOrder() { return executionOrder; }
        public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScriptType() { return scriptType; }
    public void setScriptType(String scriptType) { this.scriptType = scriptType; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public List<TriggerRequest> getTriggers() { return triggers; }
    public void setTriggers(List<TriggerRequest> triggers) { this.triggers = triggers; }
}
