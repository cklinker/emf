package com.emf.runtime.module;

import java.util.List;

/**
 * Parsed representation of a module's emf-module.json manifest.
 * Declares the module's identity, handlers, and their UI descriptors.
 *
 * @param id                unique module identifier (e.g., "stripe-integration")
 * @param name              human-readable name
 * @param version           semantic version
 * @param description       short description
 * @param author            module author
 * @param moduleClass       fully-qualified class name implementing EmfModule
 * @param minPlatformVersion minimum EMF platform version required
 * @param permissions       permissions the module requires
 * @param actionHandlers    action handler declarations with UI descriptors
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
    List<ActionHandlerManifest> actionHandlers
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
}
