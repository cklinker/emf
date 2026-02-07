package com.emf.controlplane.dto;

import com.emf.controlplane.entity.RecordType;

import java.time.Instant;

public class RecordTypeDto {

    private String id;
    private String collectionId;
    private String name;
    private String description;
    private boolean active;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;

    public RecordTypeDto() {}

    public static RecordTypeDto fromEntity(RecordType entity) {
        if (entity == null) return null;
        RecordTypeDto dto = new RecordTypeDto();
        dto.id = entity.getId();
        dto.collectionId = entity.getCollection().getId();
        dto.name = entity.getName();
        dto.description = entity.getDescription();
        dto.active = entity.isActive();
        dto.isDefault = entity.isDefault();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        return dto;
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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
