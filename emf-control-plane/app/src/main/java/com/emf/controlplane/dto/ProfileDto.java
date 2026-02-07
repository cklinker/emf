package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Profile;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileDto {

    private String id;
    private String name;
    private String description;
    private boolean system;
    private List<ObjectPermissionDto> objectPermissions;
    private List<FieldPermissionDto> fieldPermissions;
    private List<SystemPermissionDto> systemPermissions;
    private Instant createdAt;
    private Instant updatedAt;

    public ProfileDto() {}

    public static ProfileDto fromEntity(Profile profile) {
        if (profile == null) return null;
        ProfileDto dto = new ProfileDto();
        dto.id = profile.getId();
        dto.name = profile.getName();
        dto.description = profile.getDescription();
        dto.system = profile.isSystem();
        dto.createdAt = profile.getCreatedAt();
        dto.updatedAt = profile.getUpdatedAt();

        if (profile.getObjectPermissions() != null) {
            dto.objectPermissions = profile.getObjectPermissions().stream()
                    .map(ObjectPermissionDto::fromEntity)
                    .collect(Collectors.toList());
        }
        if (profile.getFieldPermissions() != null) {
            dto.fieldPermissions = profile.getFieldPermissions().stream()
                    .map(FieldPermissionDto::fromFieldPermission)
                    .collect(Collectors.toList());
        }
        if (profile.getSystemPermissions() != null) {
            dto.systemPermissions = profile.getSystemPermissions().stream()
                    .map(SystemPermissionDto::fromEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    public static ProfileDto fromEntitySummary(Profile profile) {
        if (profile == null) return null;
        ProfileDto dto = new ProfileDto();
        dto.id = profile.getId();
        dto.name = profile.getName();
        dto.description = profile.getDescription();
        dto.system = profile.isSystem();
        dto.createdAt = profile.getCreatedAt();
        dto.updatedAt = profile.getUpdatedAt();
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
