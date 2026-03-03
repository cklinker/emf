package com.emf.worker.controller;

import com.emf.jsonapi.JsonApiResponseBuilder;
import com.emf.worker.service.PrometheusQueryService;
import com.emf.worker.service.PrometheusQueryService.DataPoint;
import com.emf.worker.service.PrometheusQueryService.TimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for tenant metrics, backed by Prometheus.
 *
 * <p>Provides endpoints for range queries (chart data) and instant queries
 * (summary cards). All queries are scoped to the tenant from the
 * {@code X-Tenant-ID} header.
 *
 * <p>Returns JSON:API format consistent with other collection endpoints.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final PrometheusQueryService prometheusQueryService;

    public MetricsController(PrometheusQueryService prometheusQueryService) {
        this.prometheusQueryService = prometheusQueryService;
    }

    /**
     * Range query endpoint for chart data.
     *
     * @param tenantId the tenant ID from the gateway's X-Tenant-ID header
     * @param metric   the metric type (e.g., "requests", "latency_p50", "errors")
     * @param start    start time as ISO-8601 instant
     * @param end      end time as ISO-8601 instant
     * @param step     step duration (e.g., "60s", "5m"); auto-calculated if omitted
     * @param route    optional route filter
     * @return JSON:API single resource with time series data
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam String metric,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String route) {

        log.debug("Metrics query: tenant={}, metric={}, start={}, end={}, step={}, route={}",
                tenantId, metric, start, end, step, route);

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

        String promql = buildPromQL(metric, tenantId, route);
        if (promql == null) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request",
                            "Unknown metric type: " + metric));
        }

        List<TimeSeries> results = prometheusQueryService.queryRange(promql, startInstant, endInstant, step);

        // Convert to response
        List<Map<String, Object>> series = new ArrayList<>();
        for (TimeSeries ts : results) {
            Map<String, Object> seriesMap = new LinkedHashMap<>();
            seriesMap.put("labels", ts.labels());

            List<Map<String, Object>> points = new ArrayList<>();
            for (DataPoint dp : ts.dataPoints()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("timestamp", dp.timestamp());
                point.put("value", dp.value());
                points.add(point);
            }
            seriesMap.put("dataPoints", points);
            series.add(seriesMap);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("metric", metric);
        attributes.put("start", start);
        attributes.put("end", end);
        attributes.put("step", step);
        attributes.put("series", series);

        return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-query", metric, attributes));
    }

    /**
     * Summary endpoint for dashboard cards (instant query).
     *
     * @param tenantId the tenant ID from the gateway's X-Tenant-ID header
     * @return JSON:API single resource with summary metrics
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.debug("Metrics summary for tenant={}", tenantId);

        // Total requests today (sum of request count over last 24h)
        double totalRequests = queryScalar(
                String.format("sum(increase(emf_gateway_requests_seconds_count{tenant=\"%s\"}[24h]))", tenantId));

        // Error rate (errors / total requests * 100)
        double totalErrors = queryScalar(
                String.format("sum(increase(emf_gateway_errors_total{tenant=\"%s\"}[24h]))", tenantId));
        double errorRate = totalRequests > 0 ? (totalErrors / totalRequests) * 100 : 0;

        // Average latency (ms)
        double avgLatencySeconds = queryScalar(
                String.format("sum(rate(emf_gateway_requests_seconds_sum{tenant=\"%s\"}[5m])) / " +
                        "sum(rate(emf_gateway_requests_seconds_count{tenant=\"%s\"}[5m]))", tenantId, tenantId));
        double avgLatencyMs = avgLatencySeconds * 1000;

        // Active requests
        double activeRequests = queryScalar(
                String.format("sum(emf_gateway_requests_active{tenant=\"%s\"})", tenantId));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("totalRequests", Math.round(totalRequests));
        attributes.put("errorRate", Math.round(errorRate * 100.0) / 100.0);
        attributes.put("avgLatencyMs", Math.round(avgLatencyMs * 100.0) / 100.0);
        attributes.put("activeRequests", Math.round(activeRequests));

        return ResponseEntity.ok(JsonApiResponseBuilder.single("metrics-summary", "current", attributes));
    }

    // =========================================================================
    // PromQL builders
    // =========================================================================

    /**
     * Builds a PromQL expression for the given metric type and tenant.
     *
     * @param metric   the metric name
     * @param tenantId the tenant ID
     * @param route    optional route filter
     * @return PromQL string, or null if the metric type is unknown
     */
    String buildPromQL(String metric, String tenantId, String route) {
        String routeFilter = (route != null && !route.isBlank())
                ? String.format(",route=\"%s\"", route) : "";

        return switch (metric) {
            case "requests" ->
                    String.format("sum(rate(emf_gateway_requests_seconds_count{tenant=\"%s\"%s}[5m])) by (status)",
                            tenantId, routeFilter);
            case "requests_by_route" ->
                    String.format("sum(rate(emf_gateway_requests_seconds_count{tenant=\"%s\"}[5m])) by (route)",
                            tenantId);
            case "errors" ->
                    String.format("sum(rate(emf_gateway_errors_total{tenant=\"%s\"%s}[5m])) by (error_code)",
                            tenantId, routeFilter);
            case "latency_p50" ->
                    String.format("histogram_quantile(0.50, sum(rate(emf_gateway_requests_seconds_bucket{tenant=\"%s\"%s}[5m])) by (le))",
                            tenantId, routeFilter);
            case "latency_p95" ->
                    String.format("histogram_quantile(0.95, sum(rate(emf_gateway_requests_seconds_bucket{tenant=\"%s\"%s}[5m])) by (le))",
                            tenantId, routeFilter);
            case "latency_p99" ->
                    String.format("histogram_quantile(0.99, sum(rate(emf_gateway_requests_seconds_bucket{tenant=\"%s\"%s}[5m])) by (le))",
                            tenantId, routeFilter);
            case "latency_avg" ->
                    String.format("sum(rate(emf_gateway_requests_seconds_sum{tenant=\"%s\"%s}[5m])) / " +
                                    "sum(rate(emf_gateway_requests_seconds_count{tenant=\"%s\"%s}[5m]))",
                            tenantId, routeFilter, tenantId, routeFilter);
            case "auth_failures" ->
                    String.format("sum(rate(emf_gateway_auth_failures_total{tenant=\"%s\"}[5m])) by (reason)",
                            tenantId);
            case "rate_limit" ->
                    String.format("sum(rate(emf_gateway_ratelimit_exceeded_total{tenant=\"%s\"}[5m]))",
                            tenantId);
            case "active_requests" ->
                    String.format("emf_gateway_requests_active{tenant=\"%s\"}", tenantId);
            case "authz_denied" ->
                    String.format("sum(rate(emf_gateway_authz_denied_total{tenant=\"%s\"}[5m])) by (route)",
                            tenantId);
            default -> null;
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Runs an instant query and returns the first scalar value (or 0).
     */
    private double queryScalar(String promql) {
        List<TimeSeries> result = prometheusQueryService.queryInstant(promql);
        if (result.isEmpty() || result.get(0).dataPoints().isEmpty()) {
            return 0.0;
        }
        return result.get(0).dataPoints().get(0).value();
    }

    /**
     * Auto-calculate a reasonable step size based on the time range.
     */
    private String calculateStep(Instant start, Instant end) {
        long durationSeconds = Duration.between(start, end).getSeconds();
        if (durationSeconds <= 3600) {          // <= 1h
            return "15s";
        } else if (durationSeconds <= 21600) {  // <= 6h
            return "60s";
        } else if (durationSeconds <= 86400) {  // <= 24h
            return "5m";
        } else if (durationSeconds <= 604800) { // <= 7d
            return "30m";
        } else {                                // > 7d
            return "2h";
        }
    }
}
