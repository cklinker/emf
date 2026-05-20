package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import io.kelta.ai.service.tools.ReadToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryRecordsHandler implements ReadToolHandler {

    private static final int MAX_LIMIT = 20;
    private static final int VALUE_TRUNCATE_AT = 500;

    private final WorkerApiClient workerApiClient;

    public QueryRecordsHandler(WorkerApiClient workerApiClient) {
        this.workerApiClient = workerApiClient;
    }

    @Override
    public String name() {
        return "query_records";
    }

    @Override
    public String description() {
        return "Fetch a small sample (max 20) of records from a collection so you can see what the data actually looks like. " +
                "Use this to understand value formats, distributions, or to ground recommendations in real data. " +
                "Do NOT use for large data exports — for that direct the user to the data export feature.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string", "description", "Collection name, e.g. \"orders\""),
                        "limit", Map.of("type", "integer", "description", "Max records to return (1-20, default 5)"),
                        "fields", Map.of(
                                "type", "array",
                                "description", "Restrict returned columns to keep tokens down",
                                "items", Map.of("type", "string")
                        ),
                        "filter", Map.of(
                                "type", "object",
                                "description", "Simple equality filters, e.g. {\"status\":\"active\"}"
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

        int limit = 5;
        Object limitObj = input.get("limit");
        if (limitObj instanceof Number n) {
            limit = n.intValue();
        }
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        List<String> fields = null;
        Object fieldsObj = input.get("fields");
        if (fieldsObj instanceof List<?> list) {
            fields = list.stream().map(String::valueOf).toList();
        }

        Map<String, Object> filter = null;
        Object filterObj = input.get("filter");
        if (filterObj instanceof Map<?, ?> m) {
            filter = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                filter.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        Map<String, Object> response = workerApiClient.sampleRecords(tenantId, collectionName, limit, fields, filter);

        List<Map<String, Object>> records = new ArrayList<>();
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    records.add(truncateValues((Map<String, Object>) row));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collection", collectionName);
        result.put("count", records.size());
        result.put("records", records);
        return result;
    }

    private static Map<String, Object> truncateValues(Map<String, Object> row) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        Object id = row.get("id");
        if (id != null) attrs.put("id", id);
        Object inner = row.get("attributes");
        if (inner instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                attrs.put(String.valueOf(e.getKey()), truncate(e.getValue()));
            }
        }
        return attrs;
    }

    private static Object truncate(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        if (s.length() > VALUE_TRUNCATE_AT) {
            return s.substring(0, VALUE_TRUNCATE_AT) + "…(truncated)";
        }
        return value;
    }
}
