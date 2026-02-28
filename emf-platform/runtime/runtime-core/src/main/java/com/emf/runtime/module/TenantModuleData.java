package com.emf.runtime.module;

import java.time.Instant;
import java.util.List;

/**
 * Data record representing an installed tenant module from the database.
 *
 * @param id            primary key
 * @param tenantId      owning tenant
 * @param moduleId      module identifier from manifest
 * @param name          human-readable name
 * @param version       semantic version
 * @param description   short description
 * @param sourceUrl     original download URL
 * @param jarChecksum   SHA-256 checksum
 * @param jarSizeBytes  JAR file size
 * @param moduleClass   fully-qualified EmfModule class name
 * @param manifest      raw manifest JSON
 * @param status        lifecycle status
 * @param installedBy   user who installed the module
 * @param installedAt   installation timestamp
 * @param updatedAt     last update timestamp
 * @param actions       action handlers declared by this module
 * @since 1.0.0
 */
public record TenantModuleData(
    String id,
    String tenantId,
    String moduleId,
    String name,
    String version,
    String description,
    String sourceUrl,
    String jarChecksum,
    Long jarSizeBytes,
    String moduleClass,
    String manifest,
    String status,
    String installedBy,
    Instant installedAt,
    Instant updatedAt,
    List<TenantModuleActionData> actions
) {
    public static final String STATUS_INSTALLING = "INSTALLING";
    public static final String STATUS_INSTALLED = "INSTALLED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNINSTALLING = "UNINSTALLING";

    /**
     * Data record for an action handler provided by a module.
     */
    public record TenantModuleActionData(
        String id,
        String tenantModuleId,
        String actionKey,
        String name,
        String category,
        String description,
        String configSchema,
        String inputSchema,
        String outputSchema
    ) {}
}
