package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ConnectedApp;

import java.time.Instant;

public class ConnectedAppDto {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String clientId;
    private String redirectUris;
    private String scopes;
    private String ipRestrictions;
    private int rateLimitPerHour;
    private boolean active;
    private Instant lastUsedAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static ConnectedAppDto fromEntity(ConnectedApp entity) {
        ConnectedAppDto dto = new ConnectedAppDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setClientId(entity.getClientId());
        dto.setRedirectUris(entity.getRedirectUris());
        dto.setScopes(entity.getScopes());
        dto.setIpRestrictions(entity.getIpRestrictions());
        dto.setRateLimitPerHour(entity.getRateLimitPerHour());
        dto.setActive(entity.isActive());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getIpRestrictions() { return ipRestrictions; }
    public void setIpRestrictions(String ipRestrictions) { this.ipRestrictions = ipRestrictions; }
    public int getRateLimitPerHour() { return rateLimitPerHour; }
    public void setRateLimitPerHour(int rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
