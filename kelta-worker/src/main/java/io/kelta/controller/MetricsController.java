package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.OpenSearchQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for tenant metrics, backed by OpenSearch.
 *
 * <p>Provides endpoints for range queries (chart data) and instant queries
 * (summary cards). All queries are scoped to the tenant from the
 * {@code X-Tenant-Slug} header.
 *
 * <p>Metrics are derived from two sources in OpenSearch:
 * <ul>
 *   <li>Trace spans (jaeger-span-*) — per-request latency, errors, throughput</li>
 *   <li>Direct OTEL metrics (kelta-metrics-*) — JVM, HTTP server, custom Micrometer</li>
 * </ul>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final OpenSearchQueryService queryService;

    public MetricsController(OpenSearchQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Range query endpoint for chart data.
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam String metric,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String route) {

        log.debug("Metrics query: tenant={}, metric={}, start={}, end={}, step={}, route={}",
                tenantSlug, metric, start, end, step, route);

        Instant startInstant;
        Instant endInstant;
        try {
            startInstant = Instant.parse(start);
            endInstant = Instant.parse(end);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request",
                            "Invalid start/end format. Use ISO-8601 (e.g., 2024-01-01T00:00:00Z)"));
        }

        if (step == null || step.isBlank()) {
            step = calculateStep(startInstant, endInstant);
        }

        try {
            String interval = convertStepToInterval(step);
            List<Map<String, Object>> dataPoints = queryService.getRequestCountOverTime(
                    tenantSlug, startInstant, endInstant, interval);

            List<Map<String, Object>> series = new ArrayList<>();
            Map<String, Object> seriesMap = new LinkedHashMap<>();
            seriesMap.put("labels", Map.of("metric", metric, "tenant", tenantSlug));
            seriesMap.put("dataPoints", dataPoints);
            series.add(seriesMap);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("metric", metric);
            attributes.put("start", start);
            attributes.put("end", end);
            attributes.put("step", step);
            attributes.put("series", series);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-query", metric, attributes));
        } catch (Exception e) {
            log.error("Failed to query metrics from OpenSearch: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query metrics"));
        }
    }

    /**
     * Summary endpoint for dashboard cards.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader("X-Tenant-Slug") String tenantSlug) {

        log.debug("Metrics summary for tenant={}", tenantSlug);

        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofHours(24));

            Map<String, Object> summaryData = queryService.getMetricsSummary(tenantSlug, start, end);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("totalRequests", summaryData.getOrDefault("totalRequests", 0L));
            attributes.put("errorRate", summaryData.getOrDefault("errorRate", 0.0));
            attributes.put("avgLatencyMs", summaryData.getOrDefault("avgLatencyMs", 0.0));
            attributes.put("activeRequests", 0); // Active requests not available from historical data
            attributes.put("authFailures", summaryData.getOrDefault("authFailures", 0L));
            attributes.put("rateLimited", summaryData.getOrDefault("rateLimited", 0L));

            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-summary", "current", attributes));
        } catch (Exception e) {
            log.error("Failed to query metrics summary from OpenSearch: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query metrics summary"));
        }
    }

    /**
     * Top endpoints ranked by request count with latency percentiles.
     */
    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> topEndpoints(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofHours(24));

            List<Map<String, Object>> endpoints = queryService.getTopEndpoints(tenantSlug, start, end, limit);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("endpoints", endpoints);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-endpoints", "top", attributes));
        } catch (Exception e) {
            log.error("Failed to query top endpoints: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query top endpoints"));
        }
    }

    /**
     * Top error paths with status code breakdown.
     */
    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> topErrors(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofHours(24));

            List<Map<String, Object>> errors = queryService.getTopErrors(tenantSlug, start, end, limit);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("errors", errors);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-errors", "top", attributes));
        } catch (Exception e) {
            log.error("Failed to query top errors: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query top errors"));
        }
    }

    /**
     * Latency percentiles for a time range.
     */
    @GetMapping("/latency")
    public ResponseEntity<Map<String, Object>> latencyPercentiles(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        try {
            Instant endInstant = end != null ? Instant.parse(end) : Instant.now();
            Instant startInstant = start != null ? Instant.parse(start) : endInstant.minus(Duration.ofHours(1));

            Map<String, Double> percentiles = queryService.getLatencyPercentiles(tenantSlug, startInstant, endInstant);

            Map<String, Object> attributes = new LinkedHashMap<>(percentiles);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-latency", "percentiles", attributes));
        } catch (Exception e) {
            log.error("Failed to query latency percentiles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query latency percentiles"));
        }
    }

    private String calculateStep(Instant start, Instant end) {
        long durationSeconds = Duration.between(start, end).getSeconds();
        if (durationSeconds <= 3600) {
            return "15s";
        } else if (durationSeconds <= 21600) {
            return "60s";
        } else if (durationSeconds <= 86400) {
            return "5m";
        } else if (durationSeconds <= 604800) {
            return "30m";
        } else {
            return "2h";
        }
    }

    private String convertStepToInterval(String step) {
        // Convert Prometheus-style step to OpenSearch date histogram interval
        if (step.endsWith("s")) {
            return step;
        } else if (step.endsWith("m")) {
            return step;
        } else if (step.endsWith("h")) {
            return step;
        }
        return "1m";
    }
}
