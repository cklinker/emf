package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Service;

import java.time.Instant;

/**
 * DTO for Service entity.
 */
public class ServiceDto {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private String basePath;
    private String environment;
    private String databaseUrl;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public ServiceDto() {
    }

    public ServiceDto(Service service) {
        this.id = service.getId();
        this.name = service.getName();
        this.displayName = service.getDisplayName();
        this.description = service.getDescription();
        this.basePath = service.getBasePath();
        this.environment = service.getEnvironment();
        this.databaseUrl = service.getDatabaseUrl();
        this.active = service.isActive();
        this.createdAt = service.getCreatedAt();
        this.updatedAt = service.getUpdatedAt();
    }

    public static ServiceDto fromEntity(Service service) {
        return new ServiceDto(service);
    }

    // Getters and setters

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
}
