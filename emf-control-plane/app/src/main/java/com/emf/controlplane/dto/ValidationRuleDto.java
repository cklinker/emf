package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ValidationRule;

import java.time.Instant;

public class ValidationRuleDto {

    private String id;
    private String collectionId;
    private String name;
    private String description;
    private boolean active;
    private String errorConditionFormula;
    private String errorMessage;
    private String errorField;
    private String evaluateOn;
    private Instant createdAt;
    private Instant updatedAt;

    public ValidationRuleDto() {}

    public static ValidationRuleDto fromEntity(ValidationRule entity) {
        if (entity == null) return null;
        ValidationRuleDto dto = new ValidationRuleDto();
        dto.id = entity.getId();
        dto.collectionId = entity.getCollection().getId();
        dto.name = entity.getName();
        dto.description = entity.getDescription();
        dto.active = entity.isActive();
        dto.errorConditionFormula = entity.getErrorConditionFormula();
        dto.errorMessage = entity.getErrorMessage();
        dto.errorField = entity.getErrorField();
        dto.evaluateOn = entity.getEvaluateOn();
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

    public String getErrorConditionFormula() { return errorConditionFormula; }
    public void setErrorConditionFormula(String errorConditionFormula) { this.errorConditionFormula = errorConditionFormula; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorField() { return errorField; }
    public void setErrorField(String errorField) { this.errorField = errorField; }

    public String getEvaluateOn() { return evaluateOn; }
    public void setEvaluateOn(String evaluateOn) { this.evaluateOn = evaluateOn; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
