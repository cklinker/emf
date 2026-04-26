package io.kelta.runtime.module.integration.api;

import tools.jackson.databind.JsonNode;

/**
 * One callable operation extracted from an {@link ApiSpec}. Operations are
 * persisted as their own row so the picker can search across many specs in a
 * single trigram-backed query without parsing JSON at request time.
 *
 * @param syntheticOpId the canonical, always-present identifier — equal to
 *                      {@code operationId} when the spec provides one, otherwise
 *                      synthesized as {@code <METHOD>_<sanitized path>}
 */
public record ApiOperation(
    String id,
    String tenantId,
    String specId,
    String operationId,
    String syntheticOpId,
    String httpMethod,
    String pathTemplate,
    String summary,
    String description,
    JsonNode tags,
    JsonNode parametersSchema,
    JsonNode requestBodySchema,
    JsonNode responseSchemas,
    JsonNode securityRequired,
    boolean deprecated
) {
}
