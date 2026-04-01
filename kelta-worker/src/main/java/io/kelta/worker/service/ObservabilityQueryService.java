package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.*;

/**
 * Service for querying OpenSearch indices for observability data using Spring RestClient.
 * Replaces OpenSearchQueryService (which used the heavyweight RestHighLevelClient)
 * for AOT compatibility.
 *
 * <p>Jaeger V2 stores span tags in a nested array format:
 * {@code tags: [{key: "http.request.method", type: "string", value: "GET"}, ...]}
 * All tag queries must use OpenSearch nested queries against this structure.
 */
@Service
public class ObservabilityQueryService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityQueryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final JdbcTemplate jdbcTemplate;

    public ObservabilityQueryService(@Qualifier("opensearchRestClient") RestClient restClient,
                                     JdbcTemplate jdbcTemplate) {
        this.restClient = restClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generic search with filters, pagination, and sorting.
     */
    public SearchResult search(String indexPattern, Map<String, String> filters,
                               int page, int size, String sortField, String sortOrder) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode query = buildBoolQuery(root);

        filters.forEach((key, value) -> {
            if (value == null || value.isEmpty()) return;
            ArrayNode filterArray = getFilterArray(query);
            if (key.equals("query")) {
                getMustArray(query).add(MAPPER.createObjectNode()
                        .putObject("query_string").put("query", value));
            } else if (key.equals("@timestamp_gte")) {
                filterArray.add(MAPPER.createObjectNode()
                        .putObject("range").putObject("@timestamp").put("gte", value));
            } else if (key.equals("@timestamp_lte")) {
                filterArray.add(MAPPER.createObjectNode()
                        .putObject("range").putObject("@timestamp").put("lte", value));
            } else {
                String fieldName = isTextFieldWithKeyword(key) ? key + ".keyword" : key;
                filterArray.add(MAPPER.createObjectNode()
                        .putObject("term").put(fieldName, value));
            }
        });

        root.put("from", page * size);
        root.put("size", size);
        root.putArray("sort").addObject()
                .putObject(sortField != null ? sortField : "@timestamp")
                .put("order", sortOrder != null ? sortOrder : "desc");

        return executeSearch(indexPattern, root);
    }

    /**
     * Search for trace spans by time range and optional filters.
     */
    public SearchResult searchTraceSpans(String tenantSlug, Instant start, Instant end,
                                         Map<String, String> filters, int page, int size) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode query = buildBoolQuery(root);
        ArrayNode filterArray = getFilterArray(query);

        // Time range filter
        filterArray.add(MAPPER.createObjectNode()
                .putObject("range").putObject("startTimeMillis")
                .put("gte", start.toEpochMilli()).put("lte", end.toEpochMilli()));

        // Tenant filter
        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            filterArray.add(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        // User-provided filters
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) continue;

            switch (key) {
                case "method" -> filterArray.add(nestedTagQuery("http.request.method", value));
                case "status" -> {
                    if (value.endsWith("xx")) {
                        filterArray.add(nestedTagPrefixQuery("http.response.status_code", value.substring(0, 1)));
                    } else {
                        filterArray.add(nestedTagQuery("http.response.status_code", value));
                    }
                }
                case "path" -> {
                    ObjectNode pathBool = MAPPER.createObjectNode();
                    ArrayNode should = pathBool.putObject("bool").putArray("should");
                    should.add(nestedTagWildcardQuery("http.url.path", "*" + value + "*"));
                    should.add(nestedTagWildcardQuery("http.route", "*" + value + "*"));
                    pathBool.path("bool").asObject().put("minimum_should_match", 1);
                    filterArray.add(pathBool);
                }
                case "traceId" -> filterArray.add(MAPPER.createObjectNode()
                        .putObject("term").put("traceID", value));
                case "userId" -> filterArray.add(nestedTagQuery("kelta.user.id", value));
                default -> log.warn("Unknown filter key: {}", key);
            }
        }

        // Only server spans
        filterArray.add(MAPPER.createObjectNode()
                .putObject("term").put("tag.span@kind", "server"));

        root.put("from", page * size);
        root.put("size", size);
        root.putArray("sort").addObject()
                .putObject("startTimeMillis").put("order", "desc");

        SearchResult result = executeSearch("jaeger-span-*", root);

        // Flatten tags into tagMap for frontend
        for (Map<String, Object> hit : result.hits()) {
            hit.put("tagMap", flattenTags(hit));
        }

        return result;
    }

    /**
     * Get request count over time (date histogram aggregation).
     */
    public List<Map<String, Object>> getRequestCountOverTime(String tenantSlug,
                                                              Instant start, Instant end,
                                                              String interval) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);

        ObjectNode aggs = root.putObject("aggs");
        aggs.putObject("requests_over_time")
                .putObject("date_histogram")
                .put("field", "startTimeMillis")
                .put("fixed_interval", interval);

        JsonNode response = executeRawSearch("jaeger-span-*", root);

        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode buckets = response.path("aggregations").path("requests_over_time").path("buckets");
        for (JsonNode bucket : buckets) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", bucket.path("key_as_string").asText());
            point.put("value", bucket.path("doc_count").asLong());
            result.add(point);
        }
        return result;
    }

    /**
     * Get latency percentiles for a time range.
     */
    public Map<String, Double> getLatencyPercentiles(String tenantSlug,
                                                     Instant start, Instant end) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);

        ObjectNode aggs = root.putObject("aggs");
        aggs.putObject("latency_percentiles")
                .putObject("percentiles")
                .put("field", "duration")
                .putArray("percents").add(50).add(95).add(99);
        aggs.putObject("latency_avg")
                .putObject("avg").put("field", "duration");

        JsonNode response = executeRawSearch("jaeger-span-*", root);

        Map<String, Double> result = new HashMap<>();
        JsonNode values = response.path("aggregations").path("latency_percentiles").path("values");
        result.put("p50", values.path("50.0").asDouble());
        result.put("p95", values.path("95.0").asDouble());
        result.put("p99", values.path("99.0").asDouble());

        double avg = response.path("aggregations").path("latency_avg").path("value").asDouble();
        result.put("avg", Double.isNaN(avg) ? 0.0 : avg);

        return result;
    }

    /**
     * Get top endpoints by request count.
     */
    public List<Map<String, Object>> getTopEndpoints(String tenantSlug,
                                                      Instant start, Instant end,
                                                      int limit) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);

        ObjectNode topEndpoints = root.putObject("aggs")
                .putObject("top_endpoints");
        ObjectNode terms = topEndpoints.putObject("terms");
        terms.put("field", "operationName");
        terms.put("size", limit);

        ObjectNode subAggs = topEndpoints.putObject("aggs");
        subAggs.putObject("latency")
                .putObject("percentiles")
                .put("field", "duration")
                .putArray("percents").add(50).add(95).add(99);
        subAggs.putObject("avg_duration")
                .putObject("avg").put("field", "duration");
        subAggs.putObject("request_count")
                .putObject("value_count").put("field", "startTimeMillis");

        JsonNode response = executeRawSearch("jaeger-span-*", root);

        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode buckets = response.path("aggregations").path("top_endpoints").path("buckets");
        for (JsonNode bucket : buckets) {
            Map<String, Object> endpoint = new HashMap<>();
            endpoint.put("endpoint", bucket.path("key").asText());
            endpoint.put("requestCount", bucket.path("doc_count").asLong());

            JsonNode latencyValues = bucket.path("latency").path("values");
            endpoint.put("p50", latencyValues.path("50.0").asDouble());
            endpoint.put("p95", latencyValues.path("95.0").asDouble());
            endpoint.put("p99", latencyValues.path("99.0").asDouble());

            endpoint.put("avgDuration", bucket.path("avg_duration").path("value").asDouble());
            result.add(endpoint);
        }
        return result;
    }

    /**
     * Get top errors by operation name.
     */
    public List<Map<String, Object>> getTopErrors(String tenantSlug,
                                                   Instant start, Instant end,
                                                   int limit) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);

        // Add error filter (status >= 400) — nested range on tag value
        ArrayNode filterArray = getFilterArray(root.path("query").path("bool").asObject());
        filterArray.add(nestedTagRangeQuery("http.response.status_code", "400"));

        root.putObject("aggs")
                .putObject("error_operations")
                .putObject("terms")
                .put("field", "operationName")
                .put("size", limit);

        JsonNode response = executeRawSearch("jaeger-span-*", root);

        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode buckets = response.path("aggregations").path("error_operations").path("buckets");
        for (JsonNode bucket : buckets) {
            Map<String, Object> error = new HashMap<>();
            error.put("path", bucket.path("key").asText());
            error.put("count", bucket.path("doc_count").asLong());
            result.add(error);
        }
        return result;
    }

    /**
     * Get metrics summary (total requests, error rate, avg latency).
     */
    public Map<String, Object> getMetricsSummary(String tenantSlug,
                                                  Instant start, Instant end) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);

        ObjectNode aggs = root.putObject("aggs");
        aggs.putObject("total_requests")
                .putObject("value_count").put("field", "startTimeMillis");
        aggs.putObject("avg_duration")
                .putObject("avg").put("field", "duration");

        JsonNode response = executeRawSearch("jaeger-span-*", root);

        Map<String, Object> summary = new HashMap<>();
        long totalRequests = response.path("aggregations").path("total_requests").path("value").asLong();
        summary.put("totalRequests", totalRequests);

        double avgDuration = response.path("aggregations").path("avg_duration").path("value").asDouble();
        summary.put("avgLatencyMs", Double.isNaN(avgDuration) ? 0 : avgDuration / 1000.0);

        long errorCount = countSpansWithStatusGte(tenantSlug, start, end, "400");
        summary.put("errorCount", errorCount);
        summary.put("errorRate", totalRequests > 0
                ? (double) errorCount / totalRequests * 100.0 : 0.0);

        long authFailures = countSpansWithStatus(tenantSlug, start, end, "401") +
                countSpansWithStatus(tenantSlug, start, end, "403");
        summary.put("authFailures", authFailures);

        long rateLimited = countSpansWithStatus(tenantSlug, start, end, "429");
        summary.put("rateLimited", rateLimited);

        return summary;
    }

    /**
     * Search application logs.
     */
    public SearchResult searchLogs(Map<String, String> filters, int page, int size) {
        return search("kelta-logs-*", filters, page, size, "@timestamp", "desc");
    }

    /**
     * Get request data from PostgreSQL for a given trace ID.
     */
    public List<Map<String, Object>> getTraceRequestData(String traceId) {
        return jdbcTemplate.queryForList(
                "SELECT trace_id, span_id, method, path, status_code, " +
                        "request_headers, response_headers, request_body, response_body, " +
                        "tenant_id, user_id, user_email, correlation_id, created_at " +
                        "FROM request_data WHERE trace_id = ? ORDER BY created_at DESC",
                traceId);
    }

    // --- Private helpers ---

    private ObjectNode buildSpanBaseQuery(String tenantSlug, Instant start, Instant end) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode query = buildBoolQuery(root);
        ArrayNode filterArray = getFilterArray(query);

        filterArray.add(MAPPER.createObjectNode()
                .putObject("range").putObject("startTimeMillis")
                .put("gte", start.toEpochMilli()).put("lte", end.toEpochMilli()));
        filterArray.add(MAPPER.createObjectNode()
                .putObject("term").put("tag.span@kind", "server"));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            filterArray.add(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        return root;
    }

    private long countSpansWithStatus(String tenantSlug, Instant start, Instant end,
                                       String statusCode) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);
        getFilterArray(root.path("query").path("bool").asObject())
                .add(nestedTagQuery("http.response.status_code", statusCode));

        JsonNode response = executeRawSearch("jaeger-span-*", root);
        return response.path("hits").path("total").path("value").asLong();
    }

    private long countSpansWithStatusGte(String tenantSlug, Instant start, Instant end,
                                          String minStatusCode) {
        ObjectNode root = buildSpanBaseQuery(tenantSlug, start, end);
        root.put("size", 0);
        getFilterArray(root.path("query").path("bool").asObject())
                .add(nestedTagRangeQuery("http.response.status_code", minStatusCode));

        JsonNode response = executeRawSearch("jaeger-span-*", root);
        return response.path("hits").path("total").path("value").asLong();
    }

    private ObjectNode nestedTagQuery(String tagKey, String tagValue) {
        ObjectNode nested = MAPPER.createObjectNode();
        ObjectNode nestedObj = nested.putObject("nested");
        nestedObj.put("path", "tags");
        ObjectNode bool = nestedObj.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.addObject().putObject("term").put("tags.key", tagKey);
        must.addObject().putObject("term").put("tags.value", tagValue);
        return nested;
    }

    private ObjectNode nestedTagPrefixQuery(String tagKey, String valuePrefix) {
        ObjectNode nested = MAPPER.createObjectNode();
        ObjectNode nestedObj = nested.putObject("nested");
        nestedObj.put("path", "tags");
        ObjectNode bool = nestedObj.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.addObject().putObject("term").put("tags.key", tagKey);
        must.addObject().putObject("prefix").put("tags.value", valuePrefix);
        return nested;
    }

    private ObjectNode nestedTagWildcardQuery(String tagKey, String pattern) {
        ObjectNode nested = MAPPER.createObjectNode();
        ObjectNode nestedObj = nested.putObject("nested");
        nestedObj.put("path", "tags");
        ObjectNode bool = nestedObj.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.addObject().putObject("term").put("tags.key", tagKey);
        must.addObject().putObject("wildcard").put("tags.value", pattern);
        return nested;
    }

    private ObjectNode nestedTagRangeQuery(String tagKey, String gteValue) {
        ObjectNode nested = MAPPER.createObjectNode();
        ObjectNode nestedObj = nested.putObject("nested");
        nestedObj.put("path", "tags");
        ObjectNode bool = nestedObj.putObject("query").putObject("bool");
        ArrayNode must = bool.putArray("must");
        must.addObject().putObject("term").put("tags.key", tagKey);
        must.addObject().putObject("range").putObject("tags.value").put("gte", gteValue);
        return nested;
    }

    private ObjectNode buildBoolQuery(ObjectNode root) {
        return root.putObject("query").putObject("bool");
    }

    private ArrayNode getFilterArray(ObjectNode boolQuery) {
        if (boolQuery.has("filter")) {
            return (ArrayNode) boolQuery.get("filter");
        }
        return boolQuery.putArray("filter");
    }

    private ArrayNode getMustArray(ObjectNode boolQuery) {
        if (boolQuery.has("must")) {
            return (ArrayNode) boolQuery.get("must");
        }
        return boolQuery.putArray("must");
    }

    private SearchResult executeSearch(String indexPattern, ObjectNode query) {
        JsonNode response = executeRawSearch(indexPattern, query);

        List<Map<String, Object>> hits = new ArrayList<>();
        JsonNode hitsArray = response.path("hits").path("hits");
        for (JsonNode hit : hitsArray) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = MAPPER.convertValue(hit.path("_source"), Map.class);
            source.put("_id", hit.path("_id").asText());
            hits.add(source);
        }

        long totalHits = response.path("hits").path("total").path("value").asLong();
        return new SearchResult(hits, totalHits);
    }

    private JsonNode executeRawSearch(String indexPattern, ObjectNode query) {
        try {
            String body = MAPPER.writeValueAsString(query);
            String responseBody = restClient.post()
                    .uri("/{index}/_search", indexPattern)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.error("OpenSearch query failed for index {}: {}", indexPattern, e.getMessage(), e);
            // Build an empty response structure
            ObjectNode emptyResponse = MAPPER.createObjectNode();
            ObjectNode hits = emptyResponse.putObject("hits");
            hits.putObject("total").put("value", 0);
            hits.putArray("hits");
            return emptyResponse;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenTags(Map<String, Object> source) {
        Map<String, Object> tagMap = new LinkedHashMap<>();
        Object tagsObj = source.get("tags");
        if (tagsObj instanceof List<?> tagsList) {
            for (Object tagObj : tagsList) {
                if (tagObj instanceof Map<?, ?> tag) {
                    String key = String.valueOf(tag.get("key"));
                    Object value = tag.get("value");
                    String type = String.valueOf(tag.get("type"));
                    if ("int64".equals(type) && value instanceof Number n) {
                        tagMap.put(key, n.longValue());
                    } else if ("float64".equals(type) && value instanceof Number n) {
                        tagMap.put(key, n.doubleValue());
                    } else if ("bool".equals(type)) {
                        tagMap.put(key, Boolean.parseBoolean(String.valueOf(value)));
                    } else {
                        tagMap.put(key, value);
                    }
                }
            }
        }
        Object processObj = source.get("process");
        if (processObj instanceof Map<?, ?> process) {
            Object processTagsObj = process.get("tags");
            if (processTagsObj instanceof List<?> processTags) {
                for (Object tagObj : processTags) {
                    if (tagObj instanceof Map<?, ?> tag) {
                        String key = "process." + tag.get("key");
                        tagMap.put(key, tag.get("value"));
                    }
                }
            }
            Object serviceName = process.get("serviceName");
            if (serviceName != null) {
                tagMap.put("process.serviceName", serviceName);
            }
        }
        return tagMap;
    }

    private static boolean isTextFieldWithKeyword(String fieldName) {
        return Set.of("level", "service", "logger", "thread").contains(fieldName);
    }

    public record SearchResult(List<Map<String, Object>> hits, long totalHits) {}
}
