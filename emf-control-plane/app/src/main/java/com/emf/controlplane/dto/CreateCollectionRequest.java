package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new collection.
 */
public class CreateCollectionRequest {

    @NotBlank(message = "Collection name is required")
    @Size(min = 1, max = 100, message = "Collection name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 255, message = "Path must not exceed 255 characters")
    private String path;

    public CreateCollectionRequest() {
    }

    public CreateCollectionRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public CreateCollectionRequest(String name, String description, String path) {
        this.name = name;
        this.description = description;
        this.path = path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "CreateCollectionRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
