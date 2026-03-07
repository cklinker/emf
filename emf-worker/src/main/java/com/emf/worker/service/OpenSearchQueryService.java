package com.emf.worker.service;

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
                } else {
                    query.filter(QueryBuilders.termQuery(key, value));
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
     */
    public SearchResult searchTraceSpans(String tenantSlug, Instant start, Instant end,
                                         Map<String, String> filters,
                                         int page, int size) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
        }

        filters.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                query.filter(QueryBuilders.termQuery(key, value));
            }
        });

        sourceBuilder.query(query);
        sourceBuilder.from(page * size);
        sourceBuilder.size(size);
        sourceBuilder.sort("startTime", SortOrder.DESC);

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> hits = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            hits.add(hit.getSourceAsMap());
        }

        return new SearchResult(hits, response.getHits().getTotalHits().value);
    }

    /**
     * Get request count over time (date histogram aggregation).
     */
    public List<Map<String, Object>> getRequestCountOverTime(String tenantSlug,
                                                              Instant start, Instant end,
                                                              String interval) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);

        DateHistogramAggregationBuilder dateHistogram = AggregationBuilders
                .dateHistogram("requests_over_time")
                .field("startTime")
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
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
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
     */
    public List<Map<String, Object>> getTopEndpoints(String tenantSlug,
                                                      Instant start, Instant end,
                                                      int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.terms("top_endpoints")
                .field("tag.http.route")
                .size(limit)
                .subAggregation(AggregationBuilders.percentiles("latency").field("duration").percentiles(50, 95, 99))
                .subAggregation(AggregationBuilders.avg("avg_duration").field("duration"))
                .subAggregation(AggregationBuilders.count("request_count").field("startTime")));

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
     * Get error count by status code and path.
     */
    public List<Map<String, Object>> getTopErrors(String tenantSlug,
                                                   Instant start, Instant end,
                                                   int limit) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()))
                .filter(QueryBuilders.rangeQuery("tag.http.status_code").gte(400));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.terms("error_paths")
                .field("tag.http.route")
                .size(limit)
                .subAggregation(AggregationBuilders.terms("status_codes").field("tag.http.status_code")));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<Map<String, Object>> result = new ArrayList<>();
        Terms terms = response.getAggregations().get("error_paths");
        for (Terms.Bucket bucket : terms.getBuckets()) {
            Map<String, Object> error = new HashMap<>();
            error.put("path", bucket.getKeyAsString());
            error.put("count", bucket.getDocCount());

            Terms statusCodes = bucket.getAggregations().get("status_codes");
            Map<String, Long> codes = new HashMap<>();
            for (Terms.Bucket codeBucket : statusCodes.getBuckets()) {
                codes.put(codeBucket.getKeyAsString(), codeBucket.getDocCount());
            }
            error.put("statusCodes", codes);
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
                .filter(QueryBuilders.rangeQuery("startTime").gte(start.toEpochMilli()).lte(end.toEpochMilli()));

        if (tenantSlug != null) {
            query.filter(QueryBuilders.termQuery("tag.emf.tenant.slug", tenantSlug));
        }

        sourceBuilder.query(query);
        sourceBuilder.size(0);
        sourceBuilder.aggregation(AggregationBuilders.count("total_requests").field("startTime"));
        sourceBuilder.aggregation(AggregationBuilders.avg("avg_duration").field("duration"));
        sourceBuilder.aggregation(AggregationBuilders.filter("errors",
                QueryBuilders.rangeQuery("tag.http.status_code").gte(400)));
        sourceBuilder.aggregation(AggregationBuilders.filter("auth_failures",
                QueryBuilders.termsQuery("tag.http.status_code", (Object) 401, (Object) 403)));
        sourceBuilder.aggregation(AggregationBuilders.filter("rate_limited",
                QueryBuilders.termQuery("tag.http.status_code", 429)));

        SearchRequest request = new SearchRequest("jaeger-span-*");
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        Map<String, Object> summary = new HashMap<>();
        ValueCount totalRequests = response.getAggregations().get("total_requests");
        summary.put("totalRequests", totalRequests.getValue());

        Avg avgDuration = response.getAggregations().get("avg_duration");
        summary.put("avgLatencyMs", Double.isNaN(avgDuration.getValue()) ? 0 : avgDuration.getValue() / 1000.0);

        org.opensearch.search.aggregations.bucket.filter.Filter errors =
                response.getAggregations().get("errors");
        long errorCount = errors.getDocCount();
        summary.put("errorCount", errorCount);
        summary.put("errorRate", totalRequests.getValue() > 0
                ? (double) errorCount / totalRequests.getValue() * 100.0 : 0.0);

        org.opensearch.search.aggregations.bucket.filter.Filter authFailures =
                response.getAggregations().get("auth_failures");
        summary.put("authFailures", authFailures.getDocCount());

        org.opensearch.search.aggregations.bucket.filter.Filter rateLimited =
                response.getAggregations().get("rate_limited");
        summary.put("rateLimited", rateLimited.getDocCount());

        return summary;
    }

    public record SearchResult(List<Map<String, Object>> hits, long totalHits) {}
}
