package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Webhook;

import java.time.Instant;

public class WebhookDto {

    private String id;
    private String tenantId;
    private String name;
    private String url;
    private String events;
    private String collectionId;
    private String filterFormula;
    private String headers;
    private String secret;
    private boolean active;
    private String retryPolicy;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static WebhookDto fromEntity(Webhook entity) {
        WebhookDto dto = new WebhookDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setUrl(entity.getUrl());
        dto.setEvents(entity.getEvents());
        dto.setCollectionId(entity.getCollectionId());
        dto.setFilterFormula(entity.getFilterFormula());
        dto.setHeaders(entity.getHeaders());
        dto.setSecret(entity.getSecret());
        dto.setActive(entity.isActive());
        dto.setRetryPolicy(entity.getRetryPolicy());
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
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getEvents() { return events; }
    public void setEvents(String events) { this.events = events; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getFilterFormula() { return filterFormula; }
    public void setFilterFormula(String filterFormula) { this.filterFormula = filterFormula; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(String retryPolicy) { this.retryPolicy = retryPolicy; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
