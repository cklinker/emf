package com.emf.worker.controller;

import com.emf.worker.service.PrometheusQueryService;
import com.emf.worker.service.PrometheusQueryService.DataPoint;
import com.emf.worker.service.PrometheusQueryService.TimeSeries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MetricsController}.
 *
 * <p>Verifies the metrics query and summary endpoints, including
 * PromQL construction, parameter validation, and JSON:API response format.
 */
class MetricsControllerTest {

    private PrometheusQueryService prometheusQueryService;
    private MetricsController controller;

    @BeforeEach
    void setUp() {
        prometheusQueryService = mock(PrometheusQueryService.class);
        controller = new MetricsController(prometheusQueryService);
    }

    /** Extracts the attributes map from a JSON:API single-resource response body. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> body) {
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    // ==================== Query Tests ====================

    @Nested
    @DisplayName("GET /api/metrics/query")
    class QueryTests {

        @Test
        @DisplayName("Should return JSON:API envelope with type 'metrics-query'")
        void returnsJsonApiEnvelope() {
            when(prometheusQueryService.queryRange(anyString(), any(), any(), anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).containsKey("data");
            Map<String, Object> data = getData(body);
            assertThat(data.get("type")).isEqualTo("metrics-query");
            assertThat(data.get("id")).isEqualTo("requests");
        }

        @Test
        @DisplayName("Should return time series data in attributes")
        void returnsTimeSeriesData() {
            List<TimeSeries> mockResult = List.of(
                    new TimeSeries(
                            Map.of("status", "200"),
                            List.of(new DataPoint(1704067200.0, 42.5), new DataPoint(1704067260.0, 38.1))
                    )
            );
            when(prometheusQueryService.queryRange(anyString(), any(), any(), anyString()))
                    .thenReturn(mockResult);

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs.get("metric")).isEqualTo("requests");
            assertThat(attrs.get("start")).isEqualTo("2024-01-01T00:00:00Z");
            assertThat(attrs.get("end")).isEqualTo("2024-01-01T01:00:00Z");
            assertThat(attrs.get("step")).isEqualTo("60s");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> series = (List<Map<String, Object>>) attrs.get("series");
            assertThat(series).hasSize(1);

            @SuppressWarnings("unchecked")
            Map<String, String> labels = (Map<String, String>) series.get(0).get("labels");
            assertThat(labels.get("status")).isEqualTo("200");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) series.get(0).get("dataPoints");
            assertThat(points).hasSize(2);
            assertThat((double) points.get(0).get("value")).isEqualTo(42.5);
        }

        @Test
        @DisplayName("Should reject invalid start/end format")
        void rejectsInvalidDateFormat() {
            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "not-a-date", "also-not-a-date",
                    "60s", null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should reject unknown metric type")
        void rejectsUnknownMetric() {
            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "nonexistent_metric",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should auto-calculate step when not provided")
        void autoCalculatesStep() {
            when(prometheusQueryService.queryRange(anyString(), any(), any(), anyString()))
                    .thenReturn(List.of());

            // 1 hour range → should use 15s step
            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    null, null);

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs.get("step")).isEqualTo("15s");
        }

        @Test
        @DisplayName("Should pass route filter to PromQL")
        void passesRouteFilter() {
            when(prometheusQueryService.queryRange(anyString(), any(), any(), anyString()))
                    .thenReturn(List.of());

            controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", "/api/users");

            verify(prometheusQueryService).queryRange(
                    contains("route=\"/api/users\""), any(), any(), eq("60s"));
        }

        @Test
        @DisplayName("Should return empty series when Prometheus returns no data")
        void returnsEmptySeriesWhenNoData() {
            when(prometheusQueryService.queryRange(anyString(), any(), any(), anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            Map<String, Object> attrs = getAttributes(response.getBody());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> series = (List<Map<String, Object>>) attrs.get("series");
            assertThat(series).isEmpty();
        }
    }

    // ==================== PromQL Construction Tests ====================

    @Nested
    @DisplayName("PromQL construction")
    class PromQLTests {

        @Test
        @DisplayName("Should build requests PromQL with tenant filter")
        void buildsRequestsPromql() {
            String promql = controller.buildPromQL("requests", "tenant-1", null);
            assertThat(promql).contains("emf_gateway_requests_seconds_count");
            assertThat(promql).contains("tenant=\"tenant-1\"");
            assertThat(promql).contains("by (status)");
        }

        @Test
        @DisplayName("Should build latency_p95 PromQL with histogram_quantile")
        void buildsLatencyP95Promql() {
            String promql = controller.buildPromQL("latency_p95", "tenant-1", null);
            assertThat(promql).contains("histogram_quantile(0.95");
            assertThat(promql).contains("emf_gateway_requests_seconds_bucket");
            assertThat(promql).contains("tenant=\"tenant-1\"");
        }

        @Test
        @DisplayName("Should build errors PromQL with error_code grouping")
        void buildsErrorsPromql() {
            String promql = controller.buildPromQL("errors", "tenant-1", null);
            assertThat(promql).contains("emf_gateway_errors_total");
            assertThat(promql).contains("tenant=\"tenant-1\"");
            assertThat(promql).contains("by (error_code)");
        }

        @Test
        @DisplayName("Should build auth_failures PromQL with reason grouping")
        void buildsAuthFailuresPromql() {
            String promql = controller.buildPromQL("auth_failures", "tenant-1", null);
            assertThat(promql).contains("emf_gateway_auth_failures_total");
            assertThat(promql).contains("tenant=\"tenant-1\"");
            assertThat(promql).contains("by (reason)");
        }

        @Test
        @DisplayName("Should build rate_limit PromQL")
        void buildsRateLimitPromql() {
            String promql = controller.buildPromQL("rate_limit", "tenant-1", null);
            assertThat(promql).contains("emf_gateway_ratelimit_exceeded_total");
            assertThat(promql).contains("tenant=\"tenant-1\"");
        }

        @Test
        @DisplayName("Should build active_requests PromQL")
        void buildsActiveRequestsPromql() {
            String promql = controller.buildPromQL("active_requests", "tenant-1", null);
            assertThat(promql).contains("emf_gateway_requests_active");
            assertThat(promql).contains("tenant=\"tenant-1\"");
        }

        @Test
        @DisplayName("Should add route filter when provided")
        void addsRouteFilter() {
            String promql = controller.buildPromQL("requests", "tenant-1", "/api/users");
            assertThat(promql).contains("route=\"/api/users\"");
        }

        @Test
        @DisplayName("Should return null for unknown metric")
        void returnsNullForUnknown() {
            String promql = controller.buildPromQL("unknown", "tenant-1", null);
            assertThat(promql).isNull();
        }
    }

    // ==================== Summary Tests ====================

    @Nested
    @DisplayName("GET /api/metrics/summary")
    class SummaryTests {

        @Test
        @DisplayName("Should return JSON:API envelope with type 'metrics-summary'")
        void returnsJsonApiEnvelope() {
            when(prometheusQueryService.queryInstant(anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> data = getData(response.getBody());
            assertThat(data.get("type")).isEqualTo("metrics-summary");
            assertThat(data.get("id")).isEqualTo("current");
        }

        @Test
        @DisplayName("Should return summary metrics with correct keys")
        void returnsSummaryMetrics() {
            // Mock total requests
            when(prometheusQueryService.queryInstant(contains("emf_gateway_requests_seconds_count")))
                    .thenReturn(List.of(new TimeSeries(Map.of(), List.of(new DataPoint(0, 1500)))));

            // Mock total errors
            when(prometheusQueryService.queryInstant(contains("emf_gateway_errors_total")))
                    .thenReturn(List.of(new TimeSeries(Map.of(), List.of(new DataPoint(0, 15)))));

            // Mock avg latency (sum / count)
            when(prometheusQueryService.queryInstant(contains("emf_gateway_requests_seconds_sum")))
                    .thenReturn(List.of(new TimeSeries(Map.of(), List.of(new DataPoint(0, 0.150)))));

            // Mock active requests
            when(prometheusQueryService.queryInstant(contains("emf_gateway_requests_active")))
                    .thenReturn(List.of(new TimeSeries(Map.of(), List.of(new DataPoint(0, 5)))));

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs).containsKeys("totalRequests", "errorRate", "avgLatencyMs", "activeRequests");
        }

        @Test
        @DisplayName("Should return zeros when Prometheus returns no data")
        void returnsZerosWhenNoData() {
            when(prometheusQueryService.queryInstant(anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(((Number) attrs.get("totalRequests")).longValue()).isEqualTo(0);
            assertThat(((Number) attrs.get("errorRate")).doubleValue()).isEqualTo(0.0);
            assertThat(((Number) attrs.get("avgLatencyMs")).doubleValue()).isEqualTo(0.0);
            assertThat(((Number) attrs.get("activeRequests")).longValue()).isEqualTo(0);
        }
    }
}
