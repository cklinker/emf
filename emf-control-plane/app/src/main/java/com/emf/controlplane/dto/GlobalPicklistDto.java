package com.emf.controlplane.dto;

import com.emf.controlplane.entity.GlobalPicklist;

import java.time.Instant;

public class GlobalPicklistDto {

    private String id;
    private String tenantId;
    private String name;
    private String description;
    private boolean sorted;
    private boolean restricted;
    private Instant createdAt;
    private Instant updatedAt;

    public GlobalPicklistDto() {}

    public static GlobalPicklistDto fromEntity(GlobalPicklist entity) {
        if (entity == null) return null;
        GlobalPicklistDto dto = new GlobalPicklistDto();
        dto.id = entity.getId();
        dto.tenantId = entity.getTenantId();
        dto.name = entity.getName();
        dto.description = entity.getDescription();
        dto.sorted = entity.isSorted();
        dto.restricted = entity.isRestricted();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
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

    public boolean isSorted() { return sorted; }
    public void setSorted(boolean sorted) { this.sorted = sorted; }

    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
