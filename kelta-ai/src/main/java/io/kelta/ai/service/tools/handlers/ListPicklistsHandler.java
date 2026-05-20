package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListPicklistsHandler implements ReadToolHandler {

    private final WorkerApiClient workerApiClient;

    public ListPicklistsHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "list_picklists";
    }

    @Override
    public String description() {
        return "List global picklists available in this tenant. " +
                "Useful when proposing a PICKLIST or MULTI_PICKLIST field to see if a reusable picklist already exists.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(String tenantId, String userId, Map<String, Object> input) {
        List<Map<String, Object>> raw = workerApiClient.listPicklists(tenantId);
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Map<String, Object> p : raw) {
            Map<String, Object> attrs = (Map<String, Object>) p.getOrDefault("attributes", Map.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", p.get("id"));
            summary.put("name", attrs.get("name"));
            if (attrs.get("description") != null) summary.put("description", attrs.get("description"));
            if (attrs.get("restricted") != null) summary.put("restricted", attrs.get("restricted"));
            result.add(summary);
        }
        return result;
    }
}
