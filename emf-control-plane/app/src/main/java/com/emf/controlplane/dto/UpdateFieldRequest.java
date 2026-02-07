package com.emf.controlplane.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing field in a collection.
 * All fields are optional - only provided fields will be updated.
 */
public class UpdateFieldRequest {

    @Size(min = 1, max = 100, message = "Field name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "Field name must start with a letter and contain only letters, numbers, and underscores")
    private String name;

    @Pattern(regexp = "^(string|number|boolean|date|datetime|reference|array|object)$", 
             message = "Field type must be one of: string, number, boolean, date, datetime, reference, array, object")
    private String type;

    private Boolean required;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * JSON string containing field constraints.
     * Examples:
     * - For string: {"minLength": 1, "maxLength": 100, "pattern": "^[a-z]+$"}
     * - For number: {"min": 0, "max": 100}
     * - For reference: {"collection": "other-collection-id"}
     * - For array: {"itemType": "string", "minItems": 0, "maxItems": 10}
     */
    private String constraints;

    private Boolean trackHistory;

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

    public Boolean getTrackHistory() {
        return trackHistory;
    }

    public void setTrackHistory(Boolean trackHistory) {
        this.trackHistory = trackHistory;
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
