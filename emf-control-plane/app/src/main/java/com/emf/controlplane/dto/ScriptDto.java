package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Script;
import com.emf.controlplane.entity.ScriptTrigger;

import java.time.Instant;
import java.util.List;

public class ScriptDto {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String scriptType;
    private String language;
    private String sourceCode;
    private boolean active;
    private int version;
    private String createdBy;
    private List<TriggerDto> triggers;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScriptDto fromEntity(Script entity) {
        ScriptDto dto = new ScriptDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setScriptType(entity.getScriptType());
        dto.setLanguage(entity.getLanguage());
        dto.setSourceCode(entity.getSourceCode());
        dto.setActive(entity.isActive());
        dto.setVersion(entity.getVersion());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setTriggers(entity.getTriggers().stream().map(TriggerDto::fromEntity).toList());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public static class TriggerDto {
        private String id;
        private String collectionId;
        private String triggerEvent;
        private int executionOrder;
        private boolean active;

        public static TriggerDto fromEntity(ScriptTrigger entity) {
            TriggerDto dto = new TriggerDto();
            dto.setId(entity.getId());
            dto.setCollectionId(entity.getCollection() != null ? entity.getCollection().getId() : null);
            dto.setTriggerEvent(entity.getTriggerEvent());
            dto.setExecutionOrder(entity.getExecutionOrder());
            dto.setActive(entity.isActive());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCollectionId() { return collectionId; }
        public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
        public String getTriggerEvent() { return triggerEvent; }
        public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
        public int getExecutionOrder() { return executionOrder; }
        public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<TriggerDto> getTriggers() { return triggers; }
    public void setTriggers(List<TriggerDto> triggers) { this.triggers = triggers; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
