package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new service.
 */
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(min = 1, max = 100, message = "Service name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Service name must contain only lowercase letters, numbers, and hyphens")
    private String name;

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Base path must not exceed 100 characters")
    @Pattern(regexp = "^/[a-z0-9/-]*$", message = "Base path must start with / and contain only lowercase letters, numbers, hyphens, and slashes")
    private String basePath = "/api";

    @Size(max = 50, message = "Environment must not exceed 50 characters")
    private String environment;

    @Size(max = 500, message = "Database URL must not exceed 500 characters")
    private String databaseUrl;

    public CreateServiceRequest() {
    }

    public CreateServiceRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters and setters

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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }
}
