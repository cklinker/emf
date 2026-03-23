package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates an OpenAPI 3.0 specification from collection definitions.
 *
 * @since 1.0.0
 */
@Service
public class OpenApiGenerator {

    private static final Map<FieldType, Map<String, String>> TYPE_MAPPING = Map.ofEntries(
            Map.entry(FieldType.STRING, Map.of("type", "string")),
            Map.entry(FieldType.INTEGER, Map.of("type", "integer")),
            Map.entry(FieldType.LONG, Map.of("type", "integer", "format", "int64")),
            Map.entry(FieldType.DOUBLE, Map.of("type", "number", "format", "double")),
            Map.entry(FieldType.BOOLEAN, Map.of("type", "boolean")),
            Map.entry(FieldType.DATE, Map.of("type", "string", "format", "date")),
            Map.entry(FieldType.DATETIME, Map.of("type", "string", "format", "date-time")),
            Map.entry(FieldType.JSON, Map.of("type", "object"))
    );

    /**
     * Generates an OpenAPI 3.0 specification from the given collections.
     */
    public Map<String, Object> generate(Collection<CollectionDefinition> collections, String serverUrl) {
        var spec = new LinkedHashMap<String, Object>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "Kelta Platform API",
                "description", "Auto-generated API documentation from collection schemas",
                "version", "1.0.0"
        ));

        if (serverUrl != null && !serverUrl.isBlank()) {
            spec.put("servers", List.of(Map.of("url", serverUrl)));
        }

        // Security scheme
        spec.put("components", Map.of(
                "securitySchemes", Map.of(
                        "bearerAuth", Map.of(
                                "type", "http",
                                "scheme", "bearer",
                                "bearerFormat", "JWT"
                        )
                ),
                "schemas", generateSchemas(collections)
        ));
        spec.put("security", List.of(Map.of("bearerAuth", List.of())));

        // Paths
        spec.put("paths", generatePaths(collections));

        return spec;
    }

    private Map<String, Object> generatePaths(Collection<CollectionDefinition> collections) {
        var paths = new LinkedHashMap<String, Object>();

        for (CollectionDefinition col : collections) {
            if (col.systemCollection()) continue;

            String basePath = "/api/" + col.name();

            // List + Create
            var listOps = new LinkedHashMap<String, Object>();
            listOps.put("get", listOperation(col));
            if (!col.readOnly()) {
                listOps.put("post", createOperation(col));
            }
            paths.put(basePath, listOps);

            // Get + Update + Delete by ID
            var itemOps = new LinkedHashMap<String, Object>();
            itemOps.put("get", getByIdOperation(col));
            if (!col.readOnly()) {
                itemOps.put("put", updateOperation(col, "put"));
                itemOps.put("patch", updateOperation(col, "patch"));
                itemOps.put("delete", deleteOperation(col));
            }
            paths.put(basePath + "/{id}", itemOps);
        }

        // Atomic Operations endpoint
        paths.put("/api/operations", Map.of(
                "post", atomicOperationsEndpoint()
        ));

        return paths;
    }

    private Map<String, Object> listOperation(CollectionDefinition col) {
        return Map.of(
                "summary", "List " + col.displayName(),
                "description", col.description() != null ? col.description() : "",
                "tags", List.of(col.displayName()),
                "parameters", List.of(
                        queryParam("page[number]", "integer", "Page number (1-based)"),
                        queryParam("page[size]", "integer", "Page size (default 25)"),
                        queryParam("sort", "string", "Sort field (prefix with - for descending)"),
                        queryParam("include", "string", "Related resources to include (comma-separated)")
                ),
                "responses", Map.of(
                        "200", Map.of("description", "List of " + col.name() + " resources")
                )
        );
    }

    private Map<String, Object> getByIdOperation(CollectionDefinition col) {
        return Map.of(
                "summary", "Get " + col.displayName() + " by ID",
                "tags", List.of(col.displayName()),
                "parameters", List.of(pathParam("id", "Resource ID (UUID)")),
                "responses", Map.of(
                        "200", Map.of("description", "Single " + col.name() + " resource"),
                        "404", Map.of("description", "Not found")
                )
        );
    }

    private Map<String, Object> createOperation(CollectionDefinition col) {
        return Map.of(
                "summary", "Create " + col.displayName(),
                "tags", List.of(col.displayName()),
                "requestBody", Map.of(
                        "required", true,
                        "content", Map.of("application/vnd.api+json", Map.of(
                                "schema", Map.of("$ref", "#/components/schemas/" + col.name() + "Request")
                        ))
                ),
                "responses", Map.of(
                        "201", Map.of("description", "Created"),
                        "422", Map.of("description", "Validation error")
                )
        );
    }

    private Map<String, Object> updateOperation(CollectionDefinition col, String method) {
        String summary = "put".equals(method)
                ? "Replace " + col.displayName()
                : "Update " + col.displayName();
        return Map.of(
                "summary", summary,
                "tags", List.of(col.displayName()),
                "parameters", List.of(pathParam("id", "Resource ID (UUID)")),
                "requestBody", Map.of(
                        "required", true,
                        "content", Map.of("application/vnd.api+json", Map.of(
                                "schema", Map.of("$ref", "#/components/schemas/" + col.name() + "Request")
                        ))
                ),
                "responses", Map.of(
                        "200", Map.of("description", "Updated"),
                        "404", Map.of("description", "Not found"),
                        "422", Map.of("description", "Validation error")
                )
        );
    }

    private Map<String, Object> deleteOperation(CollectionDefinition col) {
        return Map.of(
                "summary", "Delete " + col.displayName(),
                "tags", List.of(col.displayName()),
                "parameters", List.of(pathParam("id", "Resource ID (UUID)")),
                "responses", Map.of(
                        "204", Map.of("description", "Deleted"),
                        "404", Map.of("description", "Not found")
                )
        );
    }

    private Map<String, Object> atomicOperationsEndpoint() {
        return Map.of(
                "summary", "Execute Atomic Operations (bulk CRUD)",
                "description", "JSON:API Atomic Operations extension — execute multiple create/update/delete operations in a single transaction",
                "tags", List.of("Atomic Operations"),
                "requestBody", Map.of(
                        "required", true,
                        "content", Map.of("application/vnd.api+json", Map.of(
                                "schema", Map.of("type", "object", "properties", Map.of(
                                        "atomic:operations", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "object", "properties", Map.of(
                                                        "op", Map.of("type", "string", "enum", List.of("add", "update", "remove")),
                                                        "ref", Map.of("type", "object"),
                                                        "data", Map.of("type", "object")
                                                ))
                                        )
                                ))
                        ))
                ),
                "responses", Map.of(
                        "200", Map.of("description", "All operations succeeded"),
                        "422", Map.of("description", "Operation failed — all rolled back")
                )
        );
    }

    private Map<String, Object> generateSchemas(Collection<CollectionDefinition> collections) {
        var schemas = new LinkedHashMap<String, Object>();
        for (CollectionDefinition col : collections) {
            if (col.systemCollection()) continue;
            schemas.put(col.name() + "Request", generateRequestSchema(col));
        }
        return schemas;
    }

    private Map<String, Object> generateRequestSchema(CollectionDefinition col) {
        var properties = new LinkedHashMap<String, Object>();
        for (FieldDefinition field : col.fields()) {
            properties.put(field.name(), mapFieldType(field));
        }

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "type", Map.of("type", "string", "example", col.name()),
                                        "attributes", Map.of("type", "object", "properties", properties)
                                )
                        )
                )
        );
    }

    private Map<String, Object> mapFieldType(FieldDefinition field) {
        var mapped = new LinkedHashMap<String, Object>();
        var typeInfo = TYPE_MAPPING.getOrDefault(field.type(), Map.of("type", "string"));
        mapped.putAll(typeInfo);
        if (field.nullable()) {
            mapped.put("nullable", true);
        }
        return mapped;
    }

    private Map<String, Object> queryParam(String name, String type, String description) {
        return Map.of("name", name, "in", "query", "required", false,
                "schema", Map.of("type", type), "description", description);
    }

    private Map<String, Object> pathParam(String name, String description) {
        return Map.of("name", name, "in", "path", "required", true,
                "schema", Map.of("type", "string"), "description", description);
    }
}
