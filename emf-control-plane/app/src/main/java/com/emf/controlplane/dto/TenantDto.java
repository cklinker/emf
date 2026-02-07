package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Tenant;

import java.time.Instant;

/**
 * DTO for Tenant entity.
 */
public class TenantDto {

    private String id;
    private String slug;
    private String name;
    private String edition;
    private String status;
    private String settings;
    private String limits;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantDto() {
    }

    public TenantDto(Tenant tenant) {
        this.id = tenant.getId();
        this.slug = tenant.getSlug();
        this.name = tenant.getName();
        this.edition = tenant.getEdition();
        this.status = tenant.getStatus();
        this.settings = tenant.getSettings();
        this.limits = tenant.getLimits();
        this.createdAt = tenant.getCreatedAt();
        this.updatedAt = tenant.getUpdatedAt();
    }

    public static TenantDto fromEntity(Tenant tenant) {
        return new TenantDto(tenant);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public String getLimits() {
        return limits;
    }

    public void setLimits(String limits) {
        this.limits = limits;
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
