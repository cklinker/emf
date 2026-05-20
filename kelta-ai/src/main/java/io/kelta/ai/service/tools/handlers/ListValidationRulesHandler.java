package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListValidationRulesHandler implements ReadToolHandler {

    private final WorkerApiClient workerApiClient;

    public ListValidationRulesHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "list_validation_rules";
    }

    @Override
    public String description() {
        return "List validation rules that apply to a collection. " +
                "Use when the user asks about constraints, or before proposing changes that may interact with existing rules.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string", "description", "Collection name")
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
        String collectionId = String.valueOf(collection.get("id"));

        List<Map<String, Object>> raw = workerApiClient.listValidationRules(tenantId, collectionId);
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Map<String, Object> rule : raw) {
            Map<String, Object> attrs = (Map<String, Object>) rule.getOrDefault("attributes", Map.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", rule.get("id"));
            summary.put("name", attrs.get("name"));
            if (attrs.get("formula") != null) summary.put("formula", attrs.get("formula"));
            if (attrs.get("errorMessage") != null) summary.put("errorMessage", attrs.get("errorMessage"));
            if (attrs.get("active") != null) summary.put("active", attrs.get("active"));
            result.add(summary);
        }
        return result;
    }
}
