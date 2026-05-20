package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListPageLayoutsHandler implements ReadToolHandler {

    private final WorkerApiClient workerApiClient;

    public ListPageLayoutsHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "list_page_layouts";
    }

    @Override
    public String description() {
        return "List page layouts (DETAIL, EDIT, MINI, LIST) defined for a collection. " +
                "Useful before proposing a new layout to avoid duplication.";
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

        List<Map<String, Object>> raw = workerApiClient.listPageLayouts(tenantId, collectionId);
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Map<String, Object> layout : raw) {
            Map<String, Object> attrs = (Map<String, Object>) layout.getOrDefault("attributes", Map.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", layout.get("id"));
            summary.put("name", attrs.get("name"));
            summary.put("layoutType", attrs.get("layoutType"));
            if (attrs.get("isDefault") != null) summary.put("isDefault", attrs.get("isDefault"));
            result.add(summary);
        }
        return result;
    }
}
