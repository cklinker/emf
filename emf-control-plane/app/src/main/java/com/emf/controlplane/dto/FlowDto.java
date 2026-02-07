package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Flow;

import java.time.Instant;

public class FlowDto {

    private String id;
    private String name;
    private String description;
    private String flowType;
    private boolean active;
    private int version;
    private String triggerConfig;
    private String definition;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static FlowDto fromEntity(Flow entity) {
        FlowDto dto = new FlowDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setFlowType(entity.getFlowType());
        dto.setActive(entity.isActive());
        dto.setVersion(entity.getVersion());
        dto.setTriggerConfig(entity.getTriggerConfig());
        dto.setDefinition(entity.getDefinition());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getTriggerConfig() { return triggerConfig; }
    public void setTriggerConfig(String triggerConfig) { this.triggerConfig = triggerConfig; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
