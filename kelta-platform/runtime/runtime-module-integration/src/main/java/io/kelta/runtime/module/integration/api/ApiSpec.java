package io.kelta.runtime.module.integration.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Tenant-scoped record describing an imported OpenAPI 3.x specification. The
 * {@code parsedSpec} field carries the fully-resolved spec ($refs already
 * dereferenced) so handlers and the picker UI can read it without re-parsing.
 */
public record ApiSpec(
    String id,
    String tenantId,
    String name,
    String description,
    String specVersion,
    String apiTitle,
    String apiVersion,
    String baseUrl,
    JsonNode servers,
    JsonNode securitySchemes,
    String sourceType,
    String sourceUrl,
    String rawSpec,
    String rawFormat,
    JsonNode parsedSpec,
    String specHash,
    int revision,
    boolean active,
    Instant lastImportedAt
) {
}
