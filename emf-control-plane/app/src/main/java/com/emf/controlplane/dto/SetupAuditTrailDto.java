package com.emf.controlplane.dto;

import com.emf.controlplane.entity.SetupAuditTrail;

import java.time.Instant;

public class SetupAuditTrailDto {

    private String id;
    private String userId;
    private String action;
    private String section;
    private String entityType;
    private String entityId;
    private String entityName;
    private String oldValue;
    private String newValue;
    private Instant timestamp;

    public static SetupAuditTrailDto fromEntity(SetupAuditTrail entity) {
        SetupAuditTrailDto dto = new SetupAuditTrailDto();
        dto.id = entity.getId();
        dto.userId = entity.getUserId();
        dto.action = entity.getAction();
        dto.section = entity.getSection();
        dto.entityType = entity.getEntityType();
        dto.entityId = entity.getEntityId();
        dto.entityName = entity.getEntityName();
        dto.oldValue = entity.getOldValue();
        dto.newValue = entity.getNewValue();
        dto.timestamp = entity.getTimestamp();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
