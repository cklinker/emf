package io.kelta.worker.controller;

import io.kelta.worker.service.ObservabilityQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricsControllerTest {

    private ObservabilityQueryService queryService;
    private MetricsController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(ObservabilityQueryService.class);
        controller = new MetricsController(queryService);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> body) {
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    @Nested
    @DisplayName("GET /api/metrics/query")
    class QueryTests {

        @Test
        @DisplayName("Should return JSON:API envelope with type 'metrics-query'")
        void returnsJsonApiEnvelope() {
            when(queryService.getRequestCountOverTime(anyString(), any(Instant.class), any(Instant.class), anyString()))
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
            List<Map<String, Object>> mockDataPoints = List.of(
                    Map.of("timestamp", "2024-01-01T00:00:00Z", "value", 42L),
                    Map.of("timestamp", "2024-01-01T00:01:00Z", "value", 38L)
            );
            when(queryService.getRequestCountOverTime(anyString(), any(Instant.class), any(Instant.class), anyString()))
                    .thenReturn(mockDataPoints);

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
            List<Map<String, Object>> points = (List<Map<String, Object>>) series.get(0).get("dataPoints");
            assertThat(points).hasSize(2);
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
        @DisplayName("Should auto-calculate step when not provided")
        void autoCalculatesStep() {
            when(queryService.getRequestCountOverTime(anyString(), any(Instant.class), any(Instant.class), anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    null, null);

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs.get("step")).isEqualTo("15s");
        }

        @Test
        @DisplayName("Should return empty series when no data")
        void returnsEmptySeriesWhenNoData() {
            when(queryService.getRequestCountOverTime(anyString(), any(Instant.class), any(Instant.class), anyString()))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            Map<String, Object> attrs = getAttributes(response.getBody());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> series = (List<Map<String, Object>>) attrs.get("series");
            assertThat(series).hasSize(1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) series.get(0).get("dataPoints");
            assertThat(dataPoints).isEmpty();
        }

        @Test
        @DisplayName("Should return 500 when query throws")
        void returns500OnError() {
            when(queryService.getRequestCountOverTime(anyString(), any(Instant.class), any(Instant.class), anyString()))
                    .thenThrow(new RuntimeException("Connection refused"));

            ResponseEntity<Map<String, Object>> response = controller.query(
                    "tenant-1", "requests",
                    "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z",
                    "60s", null);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("GET /api/metrics/summary")
    class SummaryTests {

        @Test
        @DisplayName("Should return JSON:API envelope with type 'metrics-summary'")
        void returnsJsonApiEnvelope() {
            when(queryService.getMetricsSummary(anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Map.of(
                            "totalRequests", 0L,
                            "errorRate", 0.0,
                            "avgLatencyMs", 0.0,
                            "errorCount", 0L,
                            "authFailures", 0L,
                            "rateLimited", 0L));

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> data = getData(response.getBody());
            assertThat(data.get("type")).isEqualTo("metrics-summary");
            assertThat(data.get("id")).isEqualTo("current");
        }

        @Test
        @DisplayName("Should return summary metrics with correct keys")
        void returnsSummaryMetrics() {
            when(queryService.getMetricsSummary(anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Map.of(
                            "totalRequests", 1500L,
                            "errorRate", 1.0,
                            "avgLatencyMs", 150.0,
                            "errorCount", 15L,
                            "authFailures", 3L,
                            "rateLimited", 2L));

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs).containsKeys("totalRequests", "errorRate", "avgLatencyMs", "activeRequests",
                    "authFailures", "rateLimited");
            assertThat(((Number) attrs.get("totalRequests")).longValue()).isEqualTo(1500L);
            assertThat(((Number) attrs.get("errorRate")).doubleValue()).isEqualTo(1.0);
            assertThat(((Number) attrs.get("avgLatencyMs")).doubleValue()).isEqualTo(150.0);
        }

        @Test
        @DisplayName("Should return 500 when query throws")
        void returns500OnError() {
            when(queryService.getMetricsSummary(anyString(), any(Instant.class), any(Instant.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            ResponseEntity<Map<String, Object>> response = controller.summary("tenant-1");

            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("GET /api/metrics/endpoints")
    class EndpointsTests {

        @Test
        @DisplayName("Should return top endpoints in JSON:API envelope")
        void returnsTopEndpoints() {
            List<Map<String, Object>> mockEndpoints = List.of(
                    Map.of("endpoint", "/api/users", "requestCount", 500L, "p50", 12.5, "p95", 45.0, "p99", 120.0, "avgDuration", 20.0),
                    Map.of("endpoint", "/api/collections", "requestCount", 300L, "p50", 8.0, "p95", 30.0, "p99", 80.0, "avgDuration", 15.0)
            );
            when(queryService.getTopEndpoints(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(mockEndpoints);

            ResponseEntity<Map<String, Object>> response = controller.topEndpoints("tenant-1", 20);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> data = getData(response.getBody());
            assertThat(data.get("type")).isEqualTo("metrics-endpoints");
            assertThat(data.get("id")).isEqualTo("top");

            Map<String, Object> attrs = getAttributes(response.getBody());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> endpoints = (List<Map<String, Object>>) attrs.get("endpoints");
            assertThat(endpoints).hasSize(2);
            assertThat(endpoints.get(0).get("endpoint")).isEqualTo("/api/users");
        }

        @Test
        @DisplayName("Should return 500 when query throws")
        void returns500OnError() {
            when(queryService.getTopEndpoints(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenThrow(new RuntimeException("Connection refused"));

            ResponseEntity<Map<String, Object>> response = controller.topEndpoints("tenant-1", 20);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("GET /api/metrics/errors")
    class ErrorsTests {

        @Test
        @DisplayName("Should return top errors in JSON:API envelope")
        void returnsTopErrors() {
            List<Map<String, Object>> mockErrors = List.of(
                    Map.of("path", "/api/users", "count", 15L, "statusCodes", Map.of("404", 10L, "500", 5L))
            );
            when(queryService.getTopErrors(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(mockErrors);

            ResponseEntity<Map<String, Object>> response = controller.topErrors("tenant-1", 20);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> data = getData(response.getBody());
            assertThat(data.get("type")).isEqualTo("metrics-errors");

            Map<String, Object> attrs = getAttributes(response.getBody());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) attrs.get("errors");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).get("path")).isEqualTo("/api/users");
        }
    }

    @Nested
    @DisplayName("GET /api/metrics/latency")
    class LatencyTests {

        @Test
        @DisplayName("Should return latency percentiles in JSON:API envelope")
        void returnsLatencyPercentiles() {
            when(queryService.getLatencyPercentiles(anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Map.of("p50", 12.5, "p95", 45.0, "p99", 120.0, "avg", 20.0));

            ResponseEntity<Map<String, Object>> response = controller.latencyPercentiles(
                    "tenant-1", "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> data = getData(response.getBody());
            assertThat(data.get("type")).isEqualTo("metrics-latency");

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(((Number) attrs.get("p50")).doubleValue()).isEqualTo(12.5);
            assertThat(((Number) attrs.get("p95")).doubleValue()).isEqualTo(45.0);
            assertThat(((Number) attrs.get("p99")).doubleValue()).isEqualTo(120.0);
        }

        @Test
        @DisplayName("Should use default time range when start/end not provided")
        void usesDefaultTimeRange() {
            when(queryService.getLatencyPercentiles(anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Map.of("p50", 10.0, "p95", 30.0, "p99", 80.0, "avg", 15.0));

            ResponseEntity<Map<String, Object>> response = controller.latencyPercentiles(
                    "tenant-1", null, null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            verify(queryService).getLatencyPercentiles(eq("tenant-1"), any(Instant.class), any(Instant.class));
        }

        @Test
        @DisplayName("Should return 500 when query throws")
        void returns500OnError() {
            when(queryService.getLatencyPercentiles(anyString(), any(Instant.class), any(Instant.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            ResponseEntity<Map<String, Object>> response = controller.latencyPercentiles(
                    "tenant-1", "2024-01-01T00:00:00Z", "2024-01-01T01:00:00Z");

            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }
}
