package com.emf.controlplane.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request DTO for updating an existing tenant.
 */
public class UpdateTenantRequest {

    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    private String name;

    @Size(max = 20, message = "Edition must not exceed 20 characters")
    private String edition;

    private Map<String, Object> settings;

    private Map<String, Object> limits;

    public UpdateTenantRequest() {
    }

    public UpdateTenantRequest(String name, String edition, Map<String, Object> settings, Map<String, Object> limits) {
        this.name = name;
        this.edition = edition;
        this.settings = settings;
        this.limits = limits;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public Map<String, Object> getLimits() {
        return limits;
    }

    public void setLimits(Map<String, Object> limits) {
        this.limits = limits;
    }

    @Override
    public String toString() {
        return "UpdateTenantRequest{" +
                "name='" + name + '\'' +
                ", edition='" + edition + '\'' +
                '}';
    }
}
