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
 * @param webhookHandlerKey the {@code ActionHandler} key that inbound webhooks posted to
 *                          {@code /api/modules/webhooks/{tenantId}/{moduleId}} are dispatched to.
 *                          Null when the module accepts no webhooks. The platform performs NO
 *                          authentication on that path — the handler owns its own trust anchor
 *                          (typically an HMAC over the raw body, verified against a credential
 *                          the module resolves itself)
 * @param uiBundlePath      classpath resource inside the same JAR holding the module's browser
 *                          bundle (e.g. {@code static/ui-bundle.js}), served to the admin UI by
 *                          {@code GET /api/modules/{moduleId}/ui-bundle.js}. Null when the module
 *                          ships no UI
 * @param services          fully-qualified names of the platform ports this module may publish
 *                          through {@code KeltaModule.getServices()}. Publishing a port that is not
 *                          declared here is refused: a module that an admin installed for one
 *                          purpose must not be able to quietly become the tenant's authority for an
 *                          unrelated one, and the declaration is what an admin can be shown and
 *                          approve. Empty means the module publishes nothing.
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
    List<CollectionManifest> collections,
    String webhookHandlerKey,
    String uiBundlePath,
    List<String> services
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
