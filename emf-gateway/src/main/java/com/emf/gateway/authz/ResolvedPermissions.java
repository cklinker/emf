package com.emf.gateway.authz;

import java.util.Collections;
import java.util.Map;

/**
 * Resolved effective permissions for a user within a tenant.
 *
 * <p>Contains system-level boolean permissions, per-collection object permissions,
 * and per-collection/per-field visibility levels. Cached in Redis with a configurable
 * TTL (default 5 minutes).
 *
 * <p>Constructed from the worker's {@code /internal/permissions} endpoint response,
 * which resolves permissions by combining profile permissions with direct and
 * group-inherited permission set permissions (most-permissive-wins).
 */
public record ResolvedPermissions(
    String userId,
    Map<String, Boolean> systemPermissions,
    Map<String, ObjectPermissions> objectPermissions,
    Map<String, Map<String, String>> fieldPermissions
) {

    /**
     * Checks whether a system permission is granted.
     *
     * @param name the permission name (e.g., "API_ACCESS", "MANAGE_USERS")
     * @return true if granted, false if not present or explicitly denied
     */
    public boolean hasSystemPermission(String name) {
        return Boolean.TRUE.equals(systemPermissions.getOrDefault(name, false));
    }

    /**
     * Gets object-level permissions for a collection.
     *
     * @param collectionId the collection UUID
     * @return the object permissions, or {@link ObjectPermissions#NONE} if not configured
     */
    public ObjectPermissions getObjectPermissions(String collectionId) {
        return objectPermissions.getOrDefault(collectionId, ObjectPermissions.NONE);
    }

    /**
     * Returns all-permissive permissions. Used when:
     * <ul>
     *   <li>Permission enforcement is disabled</li>
     *   <li>User is a platform admin</li>
     *   <li>Permission resolution fails (fail-open)</li>
     * </ul>
     */
    public static ResolvedPermissions allPermissive() {
        return new ResolvedPermissions(
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    /**
     * Returns true if this represents an all-permissive result
     * (empty maps mean no restrictions are applied).
     */
    public boolean isAllPermissive() {
        return userId == null && systemPermissions.isEmpty()
                && objectPermissions.isEmpty() && fieldPermissions.isEmpty();
    }
}
