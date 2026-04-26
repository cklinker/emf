package io.kelta.runtime.module.integration.api;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Output of {@link OpenApiSpecParser#parse}: the normalized spec metadata,
 * the dereferenced spec tree, and the full list of operations to upsert.
 */
public record ParsedSpec(
    String specVersion,
    String apiTitle,
    String apiVersion,
    String baseUrl,
    JsonNode servers,
    JsonNode securitySchemes,
    JsonNode parsedSpec,
    List<ParsedOperation> operations
) {

    /**
     * In-memory representation of an operation returned by the parser.
     * The {@code id} and {@code tenantId} on the persisted {@link ApiOperation}
     * are filled in by the storage layer.
     */
    public record ParsedOperation(
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
        boolean deprecated,
        String searchText
    ) {
    }
}
