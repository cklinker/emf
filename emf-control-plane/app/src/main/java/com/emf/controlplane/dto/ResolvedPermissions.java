package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FieldVisibility;

import java.util.Map;

/**
 * DTO representing the fully resolved permissions for a user.
 * Combines Profile + direct Permission Sets + group-inherited Permission Sets.
 */
public record ResolvedPermissions(
        Map<String, Boolean> systemPermissions,
        Map<String, ObjectPermissions> objectPermissions,
        Map<String, Map<String, FieldVisibility>> fieldPermissions
) {

    /**
     * Check if a specific system permission is granted.
     */
    public boolean hasSystemPermission(String permissionName) {
        return systemPermissions.getOrDefault(permissionName, false);
    }

    /**
     * Get object permissions for a collection.
     * Returns none() if no permissions are defined for the collection.
     */
    public ObjectPermissions getObjectPermissions(String collectionId) {
        return objectPermissions.getOrDefault(collectionId, ObjectPermissions.none());
    }

    /**
     * Get field visibility for a specific field.
     * Defaults to VISIBLE if no override is defined.
     */
    public FieldVisibility getFieldVisibility(String collectionId, String fieldId) {
        Map<String, FieldVisibility> collectionFields = fieldPermissions.get(collectionId);
        if (collectionFields == null) {
            return FieldVisibility.VISIBLE;
        }
        return collectionFields.getOrDefault(fieldId, FieldVisibility.VISIBLE);
    }
}
