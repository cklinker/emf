package io.kelta.worker.service;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.histogram.Histogram;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.metrics.Avg;
import org.opensearch.search.aggregations.metrics.Percentiles;
import org.opensearch.search.aggregations.metrics.ValueCount;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Service for querying OpenSearch indices for observability data.
 * Used by admin UI endpoints for request logs, logs, metrics, and audit data.
 *
 * <p>Jaeger V2 stores span tags in a nested array format:
 * {@code tags: [{key: "http.request.method", type: "string", value: "GET"}, ...]}
 * All tag queries must use OpenSearch nested queries against this structure.
 *
 * <p>OTEL semantic conventions (v1.21+) use:
 * <ul>
 *   <li>{@code http.request.method} (was http.method)</li>
 *   <li>{@code http.response.status_code} (was http.status_code)</li>
 *   <li>{@code http.route} (unchanged)</li>
 * </ul>
 */
@Service
public class OpenSearchQueryService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchQueryService.class);

    private final RestHighLevelClient client;

    public OpenSearchQueryService(RestHighLevelClient client) {
        this.client = client;
    }

    /**
     * Search with filters, pagination, and sorting.
     */
    public SearchResult search(String indexPattern, Map<String, String> filters,
                               int page, int size, String sortField, SortOrder sortOrder) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery();

        filters.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                if (key.equals("query")) {
                    query.must(QueryBuilders.queryStringQuery(value));
                } else if (key.equals("@timestamp_gte")) {
                    query.filter(QueryBuilders.rangeQuery("@timestamp").gte(value));
                } else if (key.equals("@timestamp_lte")) {
                    query.filter(QueryBuilders.rangeQuery("@timestamp").lte(value));
                } else {
                    // Use .keyword sub-field for text fields that have keyword mappings.
                    // Fields like "level" and "service" are mapped as text (analyzed/lowercased)
                    // so termQuery("level", "ERROR") won't match. The .keyword sub-field
                    // preserves the original case and supports exact matching.
                    String fieldName = isTextFieldWithKeyword(key) ? key + ".keyword" : key;
                    query.filter(QueryBuilders.termQuery(fieldName, value));
                }
            }
        });

        sourceBuilder.query(query);
        sourceBuilder.from(page * size);
        sourceBuilder.size(size);
        sourceBuilder.sort(sortField != null ? sortField : "@timestamp", sortOrder != null ? sortOrder : SortOrder.DESC);

        SearchRequest request = new SearchRequest(indexPattern);
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> hits = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            source.put("_id", hit.getId());
            hits.add(source);
        }

        return new SearchResult(hits, response.getHits().getTotalHits().value);
    }

    /**
     * Search for trace spans by time range and optional filters.
     *
     * <p>Filters use Jaeger V2's nested tag structure. The hits are post-processed
     * to flatten the {@code tags} array into a {@code tagMap} for easier frontend consumption.
     */
    public SearchResult searchTraceSpans(String tenantSlug, Instant start, Instant end,
                                         Map<String, String> filters,
                                         int page, int size) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        // Filter by tenant slug using nested tag query
        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        // Apply user-provided filters (also nested tag queries)
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) continue;

            switch (key) {
                case "method":
                    query.filter(nestedTagQuery("http.request.method", value));
                    break;
                case "status":
                    // Status can be exact (e.g., "404") or group (e.g., "4xx")
                    if (value.endsWith("xx")) {
                        String prefix = value.substring(0, 1);
                        query.filter(nestedTagPrefixQuery("http.response.status_code", prefix));
                    } else {
                        query.filter(nestedTagQuery("http.response.status_code", value));
                    }
                    break;
                case "path":
                    // Search both http.url.path (actual request path) and http.route (template).
                    // The performance page navigates with concrete paths like /api/products/123
                    // while http.route contains Spring MVC templates like /api/{collectionName}/{id}.
                    query.filter(QueryBuilders.boolQuery()
                            .should(nestedTagWildcardQuery("http.url.path", "*" + value + "*"))
                            .should(nestedTagWildcardQuery("http.route", "*" + value + "*"))
                            .minimumShouldMatch(1));
                    break;
                case "traceId":
                    query.filter(QueryBuilders.termQuery("traceID", value));
                    break;
                case "userId":
                    query.filter(nestedTagQuery("kelta.user.id", value));
                    break;
                default:
                    log.warn("Unknown filter key: {}", key);
            }
        }

        // Only include server spans (not internal/client spans) for the request log
        query.filter(QueryBuilders.termQuery("tag.span@kind", "server"));

        sourceBuilder.query(query);
        sourceBuilder.from(page * size);
        sourceBuilder.size(size);
        sourceBuilder.sort("startTimeMillis", SortOrder.DESC);

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> hits = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            // Flatten the nested tags array into a tagMap for easy frontend access
            source.put("tagMap", flattenTags(source));
            hits.add(source);
        }

        return new SearchResult(hits, response.getHits().getTotalHits().value);
    }

    /**
     * Creates a nested query matching a specific tag key-value pair.
     * Jaeger stores tags as: {@code tags: [{key: "...", value: "..."}]}
     */
    private org.opensearch.index.query.NestedQueryBuilder nestedTagQuery(String tagKey, String tagValue) {
        return QueryBuilders.nestedQuery("tags",
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("tags.key", tagKey))
                        .must(QueryBuilders.termQuery("tags.value", tagValue)),
                ScoreMode.None);
    }

    /**
     * Creates a nested query matching a tag value by prefix (e.g., status codes starting with "4").
     */
    private org.opensearch.index.query.NestedQueryBuilder nestedTagPrefixQuery(String tagKey, String valuePrefix) {
        return QueryBuilders.nestedQuery("tags",
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("tags.key", tagKey))
                        .must(QueryBuilders.prefixQuery("tags.value", valuePrefix)),
                ScoreMode.None);
    }

    /**
     * Creates a nested query matching a tag value by wildcard pattern.
     */
    private org.opensearch.index.query.NestedQueryBuilder nestedTagWildcardQuery(String tagKey, String pattern) {
        return QueryBuilders.nestedQuery("tags",
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("tags.key", tagKey))
                        .must(QueryBuilders.wildcardQuery("tags.value", pattern)),
                ScoreMode.None);
    }

    /**
     * Flattens the nested tags array into a simple key-value map.
     * Converts: {@code [{key: "http.method", type: "string", value: "GET"}, ...]}
     * Into: {@code {"http.method": "GET", ...}}
     */
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
                    // Convert numeric types
                    if ("int64".equals(type) && value instanceof Number) {
                        tagMap.put(key, ((Number) value).longValue());
                    } else if ("float64".equals(type) && value instanceof Number) {
                        tagMap.put(key, ((Number) value).doubleValue());
                    } else if ("bool".equals(type)) {
                        tagMap.put(key, Boolean.parseBoolean(String.valueOf(value)));
                    } else {
                        tagMap.put(key, value);
                    }
                }
            }
        }
        // Also include process tags if present
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

    /**
     * Get request count over time (date histogram aggregation).
     */
    public List<Map<String, Object>> getRequestCountOverTime(String tenantSlug,
                                                              Instant start, Instant end,
                                                              String interval) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);

        DateHistogramAggregationBuilder dateHistogram = AggregationBuilders
                .dateHistogram("requests_over_time")
                .field("startTimeMillis")
                .fixedInterval(new DateHistogramInterval(interval));

        sourceBuilder.aggregation(dateHistogram);

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> result = new ArrayList<>();
        Histogram histogram = response.getAggregations().get("requests_over_time");
        for (Histogram.Bucket bucket : histogram.getBuckets()) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", bucket.getKeyAsString());
            point.put("value", bucket.getDocCount());
            result.add(point);
        }
        return result;
    }

    /**
     * Get latency percentiles for a time range.
     */
    public Map<String, Double> getLatencyPercentiles(String tenantSlug,
                                                     Instant start, Instant end) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.percentiles("latency_percentiles")
                .field("duration")
                .percentiles(50, 95, 99));
        sourceBuilder.aggregation(AggregationBuilders.avg("latency_avg").field("duration"));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Map<String, Double> result = new HashMap<>();
        Percentiles percentiles = response.getAggregations().get("latency_percentiles");
        result.put("p50", percentiles.percentile(50));
        result.put("p95", percentiles.percentile(95));
        result.put("p99", percentiles.percentile(99));

        Avg avg = response.getAggregations().get("latency_avg");
        result.put("avg", avg.getValue());

        return result;
    }

    /**
     * Get top endpoints by request count.
     * Note: aggregation on nested tag fields requires special handling. We use
     * operationName (which contains the route template) as it's a top-level keyword field.
     */
    public List<Map<String, Object>> getTopEndpoints(String tenantSlug,
                                                      Instant start, Instant end,
                                                      int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.terms("top_endpoints")
                .field("operationName")
                .size(limit)
                .subAggregation(AggregationBuilders.percentiles("latency").field("duration").percentiles(50, 95, 99))
                .subAggregation(AggregationBuilders.avg("avg_duration").field("duration"))
                .subAggregation(AggregationBuilders.count("request_count").field("startTimeMillis")));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> result = new ArrayList<>();
        Terms terms = response.getAggregations().get("top_endpoints");
        for (Terms.Bucket bucket : terms.getBuckets()) {
            Map<String, Object> endpoint = new HashMap<>();
            endpoint.put("endpoint", bucket.getKeyAsString());
            endpoint.put("requestCount", bucket.getDocCount());

            Percentiles latency = bucket.getAggregations().get("latency");
            endpoint.put("p50", latency.percentile(50));
            endpoint.put("p95", latency.percentile(95));
            endpoint.put("p99", latency.percentile(99));

            Avg avgDuration = bucket.getAggregations().get("avg_duration");
            endpoint.put("avgDuration", avgDuration.getValue());

            result.add(endpoint);
        }
        return result;
    }

    /**
     * Get error count by operation name and status code.
     * Errors are identified by spans with http.response.status_code >= 400.
     * Uses a nested query to filter for error status codes.
     */
    public List<Map<String, Object>> getTopErrors(String tenantSlug,
                                                   Instant start, Instant end,
                                                   int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"))
                // Filter for error/client-error status codes via nested tag
                .filter(QueryBuilders.nestedQuery("tags",
                        QueryBuilders.boolQuery()
                                .must(QueryBuilders.termQuery("tags.key", "http.response.status_code"))
                                .must(QueryBuilders.rangeQuery("tags.value").gte("400")),
                        ScoreMode.None));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.terms("error_operations")
                .field("operationName")
                .size(limit));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> result = new ArrayList<>();
        Terms terms = response.getAggregations().get("error_operations");
        for (Terms.Bucket bucket : terms.getBuckets()) {
            Map<String, Object> error = new HashMap<>();
            error.put("path", bucket.getKeyAsString());
            error.put("count", bucket.getDocCount());
            result.add(error);
        }
        return result;
    }

    /**
     * Get metrics summary (total requests, error rate, avg latency).
     */
    public Map<String, Object> getMetricsSummary(String tenantSlug,
                                                  Instant start, Instant end) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.count("total_requests").field("startTimeMillis"));
        sourceBuilder.aggregation(AggregationBuilders.avg("avg_duration").field("duration"));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Map<String, Object> summary = new HashMap<>();
        ValueCount totalRequests = response.getAggregations().get("total_requests");
        summary.put("totalRequests", totalRequests.getValue());

        Avg avgDuration = response.getAggregations().get("avg_duration");
        summary.put("avgLatencyMs", Double.isNaN(avgDuration.getValue()) ? 0 : avgDuration.getValue() / 1000.0);

        // For error counts, we need a separate query with nested tag filter
        // since we can't easily combine nested aggregations
        long errorCount = countSpansWithStatusGte(tenantSlug, start, end, "400");
        summary.put("errorCount", errorCount);
        summary.put("errorRate", totalRequests.getValue() > 0
                ? (double) errorCount / totalRequests.getValue() * 100.0 : 0.0);

        long authFailures = countSpansWithStatus(tenantSlug, start, end, "401") +
                countSpansWithStatus(tenantSlug, start, end, "403");
        summary.put("authFailures", authFailures);

        long rateLimited = countSpansWithStatus(tenantSlug, start, end, "429");
        summary.put("rateLimited", rateLimited);

        return summary;
    }

    /**
     * Count server spans with a specific HTTP status code.
     */
    private long countSpansWithStatus(String tenantSlug, Instant start, Instant end,
                                       String statusCode) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"))
                .filter(nestedTagQuery("http.response.status_code", statusCode));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        return response.getHits().getTotalHits().value;
    }

    /**
     * Count server spans with HTTP status code >= given value.
     */
    private long countSpansWithStatusGte(String tenantSlug, Instant start, Instant end,
                                          String minStatusCode) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTimeMillis").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.termQuery("tag.span@kind", "server"))
                .filter(QueryBuilders.nestedQuery("tags",
                        QueryBuilders.boolQuery()
                                .must(QueryBuilders.termQuery("tags.key", "http.response.status_code"))
                                .must(QueryBuilders.rangeQuery("tags.value").gte(minStatusCode)),
                        ScoreMode.None));

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            query.filter(nestedTagQuery("kelta.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        return response.getHits().getTotalHits().value;
    }

    /**
     * Returns true for log fields mapped as {@code text} with a {@code .keyword} sub-field.
     * {@code termQuery} on a text field fails because the analyzer lowercases the indexed
     * tokens, so "ERROR" won't match the indexed "error". Using the {@code .keyword}
     * sub-field preserves the original case and supports exact matching.
     */
    private static boolean isTextFieldWithKeyword(String fieldName) {
        return Set.of("level", "service", "logger", "thread").contains(fieldName);
    }

    public record SearchResult(List<Map<String, Object>> hits, long totalHits) {}
}
