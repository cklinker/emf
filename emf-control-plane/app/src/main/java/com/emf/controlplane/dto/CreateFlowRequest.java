package com.emf.controlplane.dto;

public class CreateFlowRequest {

    private String name;
    private String description;
    private String flowType;
    private Boolean active;
    private String triggerConfig;
    private String definition;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getTriggerConfig() { return triggerConfig; }
    public void setTriggerConfig(String triggerConfig) { this.triggerConfig = triggerConfig; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
}
