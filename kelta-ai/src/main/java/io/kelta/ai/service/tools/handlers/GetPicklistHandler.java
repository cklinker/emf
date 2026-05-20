package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetPicklistHandler implements ReadToolHandler {

    private final WorkerApiClient workerApiClient;

    public GetPicklistHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "get_picklist";
    }

    @Override
    public String description() {
        return "Get the values of a global picklist by name. Useful when proposing fields that should reuse an existing picklist.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Picklist name, e.g. \"priority\"")
                ),
                "required", List.of("name")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(String tenantId, String userId, Map<String, Object> input) {
        String name = String.valueOf(input.get("name"));
        if (name.isBlank() || "null".equals(name)) {
            throw new IllegalArgumentException("name is required");
        }
        Map<String, Object> picklist = workerApiClient.getPicklist(tenantId, name)
                .orElseThrow(() -> new IllegalArgumentException("Picklist not found: " + name));

        Map<String, Object> attrs = (Map<String, Object>) picklist.getOrDefault("attributes", Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", picklist.get("id"));
        result.put("name", attrs.get("name"));
        if (attrs.get("description") != null) result.put("description", attrs.get("description"));
        if (attrs.get("restricted") != null) result.put("restricted", attrs.get("restricted"));

        List<Map<String, Object>> values = new ArrayList<>();
        Object rawValues = picklist.get("values");
        if (rawValues instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> raw) {
                    Map<String, Object> vAttrs = (Map<String, Object>) ((Map<String, Object>) raw).getOrDefault("attributes", raw);
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("value", vAttrs.get("value"));
                    v.put("label", vAttrs.get("label"));
                    if (vAttrs.get("sortOrder") != null) v.put("sortOrder", vAttrs.get("sortOrder"));
                    if (vAttrs.get("isActive") != null) v.put("isActive", vAttrs.get("isActive"));
                    values.add(v);
                }
            }
        }
        result.put("values", values);
        return result;
    }
}
