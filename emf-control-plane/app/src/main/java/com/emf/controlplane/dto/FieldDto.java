package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Field;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for Field API responses.
 * Provides a clean API representation of a Field entity.
 */
public class FieldDto {

    private String id;
    private String collectionId;
    private String name;
    private String displayName;
    private String type;
    private boolean required;
    private boolean unique;
    private boolean indexed;
    private String defaultValue;
    private String referenceTarget;
    private Integer order;
    private boolean active;
    private String description;
    private String constraints;
    private String relationshipType;
    private String relationshipName;
    private boolean cascadeDelete;
    private String referenceCollectionId;
    private String fieldTypeConfig;
    private boolean trackHistory;
    private Instant createdAt;
    private Instant updatedAt;

    public FieldDto() {
    }

    public FieldDto(String id, String collectionId, String name, String displayName, String type,
                    boolean required, boolean unique, boolean indexed, String defaultValue,
                    String referenceTarget, Integer order, boolean active, String description,
                    String constraints, String relationshipType, String relationshipName,
                    boolean cascadeDelete, String referenceCollectionId, String fieldTypeConfig,
                    boolean trackHistory, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.collectionId = collectionId;
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.required = required;
        this.unique = unique;
        this.indexed = indexed;
        this.defaultValue = defaultValue;
        this.referenceTarget = referenceTarget;
        this.order = order;
        this.active = active;
        this.description = description;
        this.constraints = constraints;
        this.relationshipType = relationshipType;
        this.relationshipName = relationshipName;
        this.cascadeDelete = cascadeDelete;
        this.referenceCollectionId = referenceCollectionId;
        this.fieldTypeConfig = fieldTypeConfig;
        this.trackHistory = trackHistory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a FieldDto from a Field entity.
     * 
     * @param field The field entity to convert
     * @return A new FieldDto with data from the entity
     */
    public static FieldDto fromEntity(Field field) {
        if (field == null) {
            return null;
        }
        return new FieldDto(
                field.getId(),
                field.getCollection() != null ? field.getCollection().getId() : null,
                field.getName(),
                field.getDisplayName(),
                field.getType(),
                field.isRequired(),
                field.isUnique(),
                field.isIndexed(),
                field.getDefaultValue(),
                field.getReferenceTarget(),
                field.getOrder(),
                field.isActive(),
                field.getDescription(),
                field.getConstraints(),
                field.getRelationshipType(),
                field.getRelationshipName(),
                field.isCascadeDelete(),
                field.getReferenceCollectionId(),
                field.getFieldTypeConfig(),
                field.isTrackHistory(),
                field.getCreatedAt(),
                field.getUpdatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getReferenceTarget() {
        return referenceTarget;
    }

    public void setReferenceTarget(String referenceTarget) {
        this.referenceTarget = referenceTarget;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public void setRelationshipName(String relationshipName) {
        this.relationshipName = relationshipName;
    }

    public boolean isCascadeDelete() {
        return cascadeDelete;
    }

    public void setCascadeDelete(boolean cascadeDelete) {
        this.cascadeDelete = cascadeDelete;
    }

    public String getReferenceCollectionId() {
        return referenceCollectionId;
    }

    public void setReferenceCollectionId(String referenceCollectionId) {
        this.referenceCollectionId = referenceCollectionId;
    }

    public String getFieldTypeConfig() {
        return fieldTypeConfig;
    }

    public void setFieldTypeConfig(String fieldTypeConfig) {
        this.fieldTypeConfig = fieldTypeConfig;
    }

    public boolean isTrackHistory() {
        return trackHistory;
    }

    public void setTrackHistory(boolean trackHistory) {
        this.trackHistory = trackHistory;
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
        return "FieldDto{" +
                "id='" + id + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", active=" + active +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldDto fieldDto = (FieldDto) o;
        return required == fieldDto.required &&
                unique == fieldDto.unique &&
                indexed == fieldDto.indexed &&
                active == fieldDto.active &&
                cascadeDelete == fieldDto.cascadeDelete &&
                trackHistory == fieldDto.trackHistory &&
                Objects.equals(id, fieldDto.id) &&
                Objects.equals(collectionId, fieldDto.collectionId) &&
                Objects.equals(name, fieldDto.name) &&
                Objects.equals(displayName, fieldDto.displayName) &&
                Objects.equals(type, fieldDto.type) &&
                Objects.equals(defaultValue, fieldDto.defaultValue) &&
                Objects.equals(referenceTarget, fieldDto.referenceTarget) &&
                Objects.equals(order, fieldDto.order) &&
                Objects.equals(description, fieldDto.description) &&
                Objects.equals(constraints, fieldDto.constraints) &&
                Objects.equals(relationshipType, fieldDto.relationshipType) &&
                Objects.equals(relationshipName, fieldDto.relationshipName) &&
                Objects.equals(referenceCollectionId, fieldDto.referenceCollectionId) &&
                Objects.equals(fieldTypeConfig, fieldDto.fieldTypeConfig) &&
                Objects.equals(createdAt, fieldDto.createdAt) &&
                Objects.equals(updatedAt, fieldDto.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, collectionId, name, displayName, type, required, unique, indexed,
                           defaultValue, referenceTarget, order, active, description, constraints,
                           relationshipType, relationshipName, cascadeDelete, referenceCollectionId,
                           fieldTypeConfig, trackHistory, createdAt, updatedAt);
    }
}
