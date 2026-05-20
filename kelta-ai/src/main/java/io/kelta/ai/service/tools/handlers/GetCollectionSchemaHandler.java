package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetCollectionSchemaHandler implements ReadToolHandler {

    private final WorkerApiClient workerApiClient;

    public GetCollectionSchemaHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "get_collection_schema";
    }

    @Override
    public String description() {
        return "Retrieve the full schema (all fields, types, picklist values, references, validations) for an existing collection. " +
                "CALL THIS before recommending fields for an existing collection so you can see what already exists. " +
                "Do not guess field names from memory.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of(
                                "type", "string",
                                "description", "Collection name (lowercase, alphanumeric, underscores), e.g. \"products\""
                        )
                ),
                "required", List.of("collectionName")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(String tenantId, String userId, Map<String, Object> input) {
        String collectionName = String.valueOf(input.get("collectionName"));
        if (collectionName.isBlank() || "null".equals(collectionName)) {
            throw new IllegalArgumentException("collectionName is required");
        }

        Map<String, Object> collection = workerApiClient.getCollectionByName(tenantId, collectionName)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found: " + collectionName));

        Map<String, Object> attrs = (Map<String, Object>) collection.getOrDefault("attributes", Map.of());
        String collectionId = String.valueOf(collection.get("id"));

        List<Map<String, Object>> rawFields = workerApiClient.listFields(tenantId, collectionId);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map<String, Object> field : rawFields) {
            Map<String, Object> fAttrs = (Map<String, Object>) field.getOrDefault("attributes", Map.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            putIfPresent(summary, "name", fAttrs.get("name"));
            putIfPresent(summary, "displayName", fAttrs.get("displayName"));
            putIfPresent(summary, "type", fAttrs.get("type"));
            putIfPresent(summary, "required", fAttrs.get("required"));
            putIfPresent(summary, "unique", fAttrs.get("uniqueConstraint"));
            putIfPresent(summary, "description", fAttrs.get("description"));
            putIfPresent(summary, "referenceTarget", fAttrs.get("referenceTarget"));
            putIfPresent(summary, "relationshipName", fAttrs.get("relationshipName"));
            putIfPresent(summary, "fieldTypeConfig", fAttrs.get("fieldTypeConfig"));
            fields.add(summary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", attrs.get("name"));
        result.put("displayName", attrs.get("displayName"));
        result.put("description", attrs.get("description"));
        result.put("displayFieldName", attrs.get("displayFieldName"));
        result.put("fieldCount", fields.size());
        result.put("fields", fields);
        return result;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
