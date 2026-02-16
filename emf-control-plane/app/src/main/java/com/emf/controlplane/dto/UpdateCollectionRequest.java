package com.emf.controlplane.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing collection.
 */
public class UpdateCollectionRequest {

    @Size(min = 1, max = 100, message = "Collection name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 36, message = "Display field ID must not exceed 36 characters")
    private String displayFieldId;

    public UpdateCollectionRequest() {
    }

    public UpdateCollectionRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayFieldId() {
        return displayFieldId;
    }

    public void setDisplayFieldId(String displayFieldId) {
        this.displayFieldId = displayFieldId;
    }

    @Override
    public String toString() {
        return "UpdateCollectionRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", displayFieldId='" + displayFieldId + '\'' +
                '}';
    }
}
