package com.emf.runtime.model;

import java.util.List;
import java.util.Objects;

/**
 * Authorization configuration for a collection.
 * 
 * <p>Controls role-based access to collection operations.
 * 
 * @param enabled Whether authorization is enabled for this collection
 * @param readRoles Roles allowed to perform read operations (LIST, GET)
 * @param writeRoles Roles allowed to perform write operations (CREATE, UPDATE, DELETE)
 * 
 * @since 1.0.0
 */
public record AuthzConfig(
    boolean enabled,
    List<String> readRoles,
    List<String> writeRoles
) {
    /**
     * Compact constructor with defensive copying.
     */
    public AuthzConfig {
        readRoles = readRoles != null ? List.copyOf(readRoles) : List.of();
        writeRoles = writeRoles != null ? List.copyOf(writeRoles) : List.of();
    }
    
    /**
     * Creates an authorization configuration with authorization disabled.
     * 
     * @return authorization configuration with authorization disabled
     */
    public static AuthzConfig disabled() {
        return new AuthzConfig(false, List.of(), List.of());
    }
    
    /**
     * Creates an authorization configuration with the specified roles.
     * 
     * @param readRoles roles allowed to read
     * @param writeRoles roles allowed to write
     * @return authorization configuration with specified roles
     */
    public static AuthzConfig withRoles(List<String> readRoles, List<String> writeRoles) {
        Objects.requireNonNull(readRoles, "readRoles cannot be null");
        Objects.requireNonNull(writeRoles, "writeRoles cannot be null");
        return new AuthzConfig(true, readRoles, writeRoles);
    }
}
