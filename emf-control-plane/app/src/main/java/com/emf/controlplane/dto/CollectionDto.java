package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Collection;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for Collection API responses.
 * Provides a clean API representation of a Collection entity.
 */
public class CollectionDto {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private String storageMode;
    private boolean active;
    private boolean systemCollection;
    private Integer currentVersion;
    private String displayFieldId;
    private String displayFieldName;
    private List<FieldDto> fields;
    private Instant createdAt;
    private Instant updatedAt;

    public CollectionDto() {
    }

    public CollectionDto(String id, String name, String displayName, String description,
                         String storageMode, boolean active, boolean systemCollection,
                         Integer currentVersion, String displayFieldId, String displayFieldName,
                         List<FieldDto> fields, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.storageMode = storageMode;
        this.active = active;
        this.systemCollection = systemCollection;
        this.currentVersion = currentVersion;
        this.displayFieldId = displayFieldId;
        this.displayFieldName = displayFieldName;
        this.fields = fields;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a CollectionDto from a Collection entity.
     *
     * @param collection The collection entity to convert
     * @return A new CollectionDto with data from the entity
     */
    public static CollectionDto fromEntity(Collection collection) {
        if (collection == null) {
            return null;
        }
        return new CollectionDto(
                collection.getId(),
                collection.getName(),
                collection.getDisplayName(),
                collection.getDescription(),
                collection.getStorageMode(),
                collection.isActive(),
                collection.isSystemCollection(),
                collection.getCurrentVersion(),
                collection.getDisplayFieldId(),
                null, // displayFieldName - resolved when fields are available
                null, // fields - populated separately to avoid circular references
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    /**
     * Creates a CollectionDto from a Collection entity with fields.
     * Resolves the display field name from the field list.
     *
     * @param collection The collection entity to convert
     * @param fields The fields for this collection
     * @return A new CollectionDto with complete data
     */
    public static CollectionDto fromEntityWithDetails(Collection collection, List<FieldDto> fields) {
        if (collection == null) {
            return null;
        }
        String displayFieldName = null;
        if (collection.getDisplayFieldId() != null && fields != null) {
            displayFieldName = fields.stream()
                    .filter(f -> f.getId().equals(collection.getDisplayFieldId()))
                    .map(FieldDto::getName)
                    .findFirst()
                    .orElse(null);
        }
        return new CollectionDto(
                collection.getId(),
                collection.getName(),
                collection.getDisplayName(),
                collection.getDescription(),
                collection.getStorageMode(),
                collection.isActive(),
                collection.isSystemCollection(),
                collection.getCurrentVersion(),
                collection.getDisplayFieldId(),
                displayFieldName,
                fields,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSystemCollection() {
        return systemCollection;
    }

    public void setSystemCollection(boolean systemCollection) {
        this.systemCollection = systemCollection;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getDisplayFieldId() {
        return displayFieldId;
    }

    public void setDisplayFieldId(String displayFieldId) {
        this.displayFieldId = displayFieldId;
    }

    public String getDisplayFieldName() {
        return displayFieldName;
    }

    public void setDisplayFieldName(String displayFieldName) {
        this.displayFieldName = displayFieldName;
    }

    public List<FieldDto> getFields() {
        return fields;
    }

    public void setFields(List<FieldDto> fields) {
        this.fields = fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CollectionDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", storageMode='" + storageMode + '\'' +
                ", active=" + active +
                ", systemCollection=" + systemCollection +
                ", currentVersion=" + currentVersion +
                ", displayFieldId='" + displayFieldId + '\'' +
                ", displayFieldName='" + displayFieldName + '\'' +
                ", fieldsCount=" + (fields != null ? fields.size() : 0) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionDto that = (CollectionDto) o;
        return active == that.active &&
                systemCollection == that.systemCollection &&
                java.util.Objects.equals(id, that.id) &&
                java.util.Objects.equals(name, that.name) &&
                java.util.Objects.equals(displayName, that.displayName) &&
                java.util.Objects.equals(description, that.description) &&
                java.util.Objects.equals(storageMode, that.storageMode) &&
                java.util.Objects.equals(currentVersion, that.currentVersion) &&
                java.util.Objects.equals(displayFieldId, that.displayFieldId) &&
                java.util.Objects.equals(displayFieldName, that.displayFieldName) &&
                java.util.Objects.equals(createdAt, that.createdAt) &&
                java.util.Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, displayName, description, storageMode, active,
                systemCollection, currentVersion, displayFieldId, displayFieldName, createdAt, updatedAt);
    }
}
