package io.kelta.runtime.module;

import java.util.List;

/**
 * Parsed representation of a module's kelta-module.json manifest.
 * Declares the module's identity, handlers, and their UI descriptors.
 *
 * @param id                unique module identifier (e.g., "stripe-integration")
 * @param name              human-readable name
 * @param version           semantic version
 * @param description       short description
 * @param author            module author
 * @param moduleClass       fully-qualified class name implementing KeltaModule
 * @param minPlatformVersion minimum Kelta platform version required
 * @param permissions       permissions the module requires
 * @param actionHandlers    action handler declarations with UI descriptors
 * @param collections       collections the module needs — created at install time via the same
 *                          metadata-driven path an admin uses (no DDL, no Flyway); see
 *                          {@link CollectionManifest}
 * @since 1.0.0
 */
public record ModuleManifest(
    String id,
    String name,
    String version,
    String description,
    String author,
    String moduleClass,
    String minPlatformVersion,
    List<String> permissions,
    List<ActionHandlerManifest> actionHandlers,
    List<CollectionManifest> collections
) {
    /**
     * Declares an action handler provided by the module.
     *
     * @param key           unique handler key (e.g., "stripe:charge")
     * @param name          human-readable name
     * @param category      grouping category for UI
     * @param description   short description
     * @param icon          lucide-react icon name (optional)
     * @param configSchema  JSON Schema for configuration form
     * @param inputSchema   JSON Schema for expected input
     * @param outputSchema  JSON Schema for handler output
     */
    public record ActionHandlerManifest(
        String key,
        String name,
        String category,
        String description,
        String icon,
        String configSchema,
        String inputSchema,
        String outputSchema
    ) {}

    /**
     * A collection the module needs in the installing tenant.
     *
     * <p>Deliberately a slim shape, not the full {@code CollectionDefinition} builder API: a
     * module declares what an admin could have created by hand through the collection UI, and
     * install creates it through that same runtime path. There is no DDL, no migration, and no
     * way for a module to reach schema powers the admin API doesn't already expose.
     *
     * <p>Creation is skipped when a collection of this name already exists in the tenant — an
     * upgrade or reinstall must not clobber a tenant's live schema or data.
     *
     * @param name        collection name; must match {@code ^[a-z][a-z0-9_]*$} (it is also the
     *                    route segment)
     * @param displayName human-readable name, defaults to a capitalized {@code name}
     * @param fields      the fields to create on it
     */
    public record CollectionManifest(
        String name,
        String displayName,
        List<FieldManifest> fields
    ) {
        /**
         * A field on a module-declared collection.
         *
         * @param name        field name (camelCase, as elsewhere in the platform)
         * @param displayName human-readable label, defaults to {@code name}
         * @param type        {@code FieldType} enum name (e.g. {@code STRING}, {@code INTEGER})
         * @param required    whether the field is required
         */
        public record FieldManifest(
            String name,
            String displayName,
            String type,
            boolean required
        ) {}
    }
}
