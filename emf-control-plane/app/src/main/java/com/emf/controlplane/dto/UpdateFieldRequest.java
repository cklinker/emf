package com.emf.controlplane.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing field in a collection.
 * All fields are optional - only provided fields will be updated.
 * Type validation is handled by FieldService.resolveFieldType() which supports
 * all 24 field types including Phase 2 types.
 */
public class UpdateFieldRequest {

    @Size(min = 1, max = 100, message = "Field name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "Field name must start with a letter and contain only letters, numbers, and underscores")
    private String name;

    @Size(min = 1, max = 50, message = "Field display name must be between 1 and 50 characters")
    private String displayName;

    // Type validation handled by FieldService.resolveFieldType() to support all 24 types
    private String type;

    private Boolean required;

    private Boolean unique;

    private Boolean indexed;

    private String defaultValue;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * JSON string containing field constraints.
     */
    private String constraints;

    /**
     * JSON string containing type-specific configuration.
     */
    private String fieldTypeConfig;

    private Boolean trackHistory;

    private Integer order;

    public UpdateFieldRequest() {
    }

    public UpdateFieldRequest(String name, String type, Boolean required, String description, String constraints) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.constraints = constraints;
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

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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

    public String getFieldTypeConfig() {
        return fieldTypeConfig;
    }

    public void setFieldTypeConfig(String fieldTypeConfig) {
        this.fieldTypeConfig = fieldTypeConfig;
    }

    public Boolean getTrackHistory() {
        return trackHistory;
    }

    public void setTrackHistory(Boolean trackHistory) {
        this.trackHistory = trackHistory;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "UpdateFieldRequest{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", description='" + description + '\'' +
                '}';
    }
}
