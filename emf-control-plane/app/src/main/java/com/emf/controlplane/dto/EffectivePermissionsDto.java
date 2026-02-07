package com.emf.controlplane.dto;

import java.util.Map;
import java.util.Set;

/**
 * DTO representing all effective permissions for a user.
 * Used by the gateway to cache and evaluate permissions.
 */
public class EffectivePermissionsDto {

    private String userId;
    private Map<String, ObjectPermissionDto> objectPermissions;
    private Map<String, String> fieldPermissions; // fieldId -> visibility
    private Set<String> systemPermissions; // granted permission keys

    public EffectivePermissionsDto() {}

    public EffectivePermissionsDto(String userId,
                                    Map<String, ObjectPermissionDto> objectPermissions,
                                    Map<String, String> fieldPermissions,
                                    Set<String> systemPermissions) {
        this.userId = userId;
        this.objectPermissions = objectPermissions;
        this.fieldPermissions = fieldPermissions;
        this.systemPermissions = systemPermissions;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, ObjectPermissionDto> getObjectPermissions() { return objectPermissions; }
    public void setObjectPermissions(Map<String, ObjectPermissionDto> objectPermissions) { this.objectPermissions = objectPermissions; }

    public Map<String, String> getFieldPermissions() { return fieldPermissions; }
    public void setFieldPermissions(Map<String, String> fieldPermissions) { this.fieldPermissions = fieldPermissions; }

    public Set<String> getSystemPermissions() { return systemPermissions; }
    public void setSystemPermissions(Set<String> systemPermissions) { this.systemPermissions = systemPermissions; }
}
