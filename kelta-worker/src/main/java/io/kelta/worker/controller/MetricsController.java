package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.ObservabilityQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for tenant metrics.
 * Queries Mimir (Prometheus-compatible API) for trace-derived span metrics.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final ObservabilityQueryService queryService;

    public MetricsController(ObservabilityQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
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
            List<Map<String, Object>> dataPoints = queryMetric(
                    metric, tenantSlug, startInstant, endInstant, step);

            List<Map<String, Object>> series = new ArrayList<>();
            Map<String, Object> seriesMap = new LinkedHashMap<>();
            seriesMap.put("labels", Map.of("metric", metric));
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
            log.error("Failed to query metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query metrics"));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug) {

        log.debug("Metrics summary for tenant={}", tenantSlug);

        try {
            Instant end = Instant.now();
            Instant start = end.minus(Duration.ofHours(24));

            Map<String, Object> summaryData = queryService.getMetricsSummary(tenantSlug, start, end);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("totalRequests", summaryData.getOrDefault("totalRequests", 0L));
            attributes.put("errorRate", summaryData.getOrDefault("errorRate", 0.0));
            attributes.put("avgLatencyMs", summaryData.getOrDefault("avgLatencyMs", 0.0));
            attributes.put("activeRequests", 0);
            attributes.put("authFailures", summaryData.getOrDefault("authFailures", 0L));
            attributes.put("rateLimited", summaryData.getOrDefault("rateLimited", 0L));

            return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-summary", "current", attributes));
        } catch (Exception e) {
            log.error("Failed to query metrics summary: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to query metrics summary"));
        }
    }

    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> topEndpoints(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
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

    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> topErrors(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
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

    @GetMapping("/latency")
    public ResponseEntity<Map<String, Object>> latencyPercentiles(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
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

    private List<Map<String, Object>> queryMetric(String metric, String tenantSlug,
                                                      Instant start, Instant end, String step) {
        return switch (metric) {
            case "requests", "requests_by_route", "request_rate" ->
                    queryService.getRequestRateOverTime(tenantSlug, start, end, step);
            case "latency_p50" ->
                    queryService.getLatencyOverTime(tenantSlug, start, end, step, 0.50);
            case "latency_p95" ->
                    queryService.getLatencyOverTime(tenantSlug, start, end, step, 0.95);
            case "latency_p99" ->
                    queryService.getLatencyOverTime(tenantSlug, start, end, step, 0.99);
            case "errors" ->
                    queryService.getErrorCountOverTime(tenantSlug, start, end, step);
            case "auth_failures" ->
                    queryService.getAuthFailuresOverTime(tenantSlug, start, end, step);
            case "rate_limit" ->
                    queryService.getRateLimitOverTime(tenantSlug, start, end, step);
            case "active_requests" ->
                    queryService.getActiveRequestsOverTime(tenantSlug, start, end, step);
            default -> {
                log.warn("Unknown metric '{}', falling back to request rate", metric);
                yield queryService.getRequestRateOverTime(tenantSlug, start, end, step);
            }
        };
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
}
