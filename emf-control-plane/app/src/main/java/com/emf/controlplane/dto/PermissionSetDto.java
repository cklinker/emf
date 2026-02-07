package com.emf.controlplane.dto;

import com.emf.controlplane.entity.PermissionSet;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class PermissionSetDto {

    private String id;
    private String name;
    private String description;
    private boolean system;
    private List<ObjectPermissionDto> objectPermissions;
    private List<FieldPermissionDto> fieldPermissions;
    private List<SystemPermissionDto> systemPermissions;
    private Instant createdAt;
    private Instant updatedAt;

    public PermissionSetDto() {}

    public static PermissionSetDto fromEntity(PermissionSet entity) {
        if (entity == null) return null;
        PermissionSetDto dto = new PermissionSetDto();
        dto.id = entity.getId();
        dto.name = entity.getName();
        dto.description = entity.getDescription();
        dto.system = entity.isSystem();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();

        if (entity.getObjectPermissions() != null) {
            dto.objectPermissions = entity.getObjectPermissions().stream()
                    .map(ObjectPermissionDto::fromPermsetEntity)
                    .collect(Collectors.toList());
        }
        if (entity.getFieldPermissions() != null) {
            dto.fieldPermissions = entity.getFieldPermissions().stream()
                    .map(FieldPermissionDto::fromPermsetFieldPermission)
                    .collect(Collectors.toList());
        }
        if (entity.getSystemPermissions() != null) {
            dto.systemPermissions = entity.getSystemPermissions().stream()
                    .map(SystemPermissionDto::fromPermsetEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    public static PermissionSetDto fromEntitySummary(PermissionSet entity) {
        if (entity == null) return null;
        PermissionSetDto dto = new PermissionSetDto();
        dto.id = entity.getId();
        dto.name = entity.getName();
        dto.description = entity.getDescription();
        dto.system = entity.isSystem();
        dto.createdAt = entity.getCreatedAt();
        dto.updatedAt = entity.getUpdatedAt();
        return dto;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public List<ObjectPermissionDto> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(List<ObjectPermissionDto> objectPermissions) { this.objectPermissions = objectPermissions; }

    public List<FieldPermissionDto> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(List<FieldPermissionDto> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public List<SystemPermissionDto> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(List<SystemPermissionDto> systemPermissions) { this.systemPermissions = systemPermissions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
