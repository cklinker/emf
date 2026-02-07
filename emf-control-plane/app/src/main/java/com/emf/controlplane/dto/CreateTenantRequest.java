package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request DTO for creating a new tenant.
 */
public class CreateTenantRequest {

    @NotBlank(message = "Slug is required")
    @Size(min = 3, max = 63, message = "Slug must be between 3 and 63 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,61}[a-z0-9]$", message = "Slug must be lowercase alphanumeric with hyphens, starting with a letter")
    private String slug;

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    private String name;

    @Size(max = 20, message = "Edition must not exceed 20 characters")
    private String edition;

    private Map<String, Object> settings;

    private Map<String, Object> limits;

    public CreateTenantRequest() {
    }

    public CreateTenantRequest(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public CreateTenantRequest(String slug, String name, String edition, Map<String, Object> settings, Map<String, Object> limits) {
        this.slug = slug;
        this.name = name;
        this.edition = edition;
        this.settings = settings;
        this.limits = limits;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
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
        return "CreateTenantRequest{" +
                "slug='" + slug + '\'' +
                ", name='" + name + '\'' +
                ", edition='" + edition + '\'' +
                '}';
    }
}
