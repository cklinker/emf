package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WorkflowActionType;

import java.time.Instant;

public class WorkflowActionTypeDto {

    private String id;
    private String key;
    private String name;
    private String description;
    private String category;
    private String configSchema;
    private String icon;
    private String handlerClass;
    private boolean active;
    private boolean builtIn;
    private boolean handlerAvailable;
    private Instant createdAt;
    private Instant updatedAt;

    public static WorkflowActionTypeDto fromEntity(WorkflowActionType entity) {
        return fromEntity(entity, false);
    }

    public static WorkflowActionTypeDto fromEntity(WorkflowActionType entity, boolean handlerAvailable) {
        WorkflowActionTypeDto dto = new WorkflowActionTypeDto();
        dto.setId(entity.getId());
        dto.setKey(entity.getKey());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setConfigSchema(entity.getConfigSchema());
        dto.setIcon(entity.getIcon());
        dto.setHandlerClass(entity.getHandlerClass());
        dto.setActive(entity.isActive());
        dto.setBuiltIn(entity.isBuiltIn());
        dto.setHandlerAvailable(handlerAvailable);
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
    public boolean isHandlerAvailable() { return handlerAvailable; }
    public void setHandlerAvailable(boolean handlerAvailable) { this.handlerAvailable = handlerAvailable; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
