package com.emf.worker.service;

import com.emf.worker.service.PrometheusQueryService.DataPoint;
import com.emf.worker.service.PrometheusQueryService.TimeSeries;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PrometheusQueryService}.
 *
 * <p>Verifies Prometheus HTTP API interaction, response parsing,
 * and error handling with mocked RestTemplate.
 */
class PrometheusQueryServiceTest {

    private RestTemplate restTemplate;
    private PrometheusQueryService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new PrometheusQueryService(restTemplate, objectMapper);
    }

    // ==================== Range Query Tests ====================

    @Nested
    @DisplayName("queryRange")
    class RangeQueryTests {

        @Test
        @DisplayName("Should parse matrix response with multiple series")
        void parsesMatrixResponse() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "matrix",
                        "result": [
                          {
                            "metric": {"status": "200"},
                            "values": [[1704067200, "42.5"], [1704067260, "38.1"]]
                          },
                          {
                            "metric": {"status": "500"},
                            "values": [[1704067200, "1.2"], [1704067260, "0.8"]]
                          }
                        ]
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryRange(
                    "test_query", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            assertThat(result).hasSize(2);

            // First series (status=200)
            assertThat(result.get(0).labels()).containsEntry("status", "200");
            assertThat(result.get(0).dataPoints()).hasSize(2);
            assertThat(result.get(0).dataPoints().get(0).value()).isEqualTo(42.5);

            // Second series (status=500)
            assertThat(result.get(1).labels()).containsEntry("status", "500");
            assertThat(result.get(1).dataPoints()).hasSize(2);
        }

        @Test
        @DisplayName("Should construct correct URL with query params")
        void constructsCorrectUrl() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn("{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");

            service.queryRange(
                    "rate(test[5m])", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            verify(restTemplate).getForObject(
                    argThat((String url) -> url.contains("/api/v1/query_range")
                            && url.contains("query=")
                            && url.contains("start=1704067200")
                            && url.contains("end=1704067320")
                            && url.contains("step=60s")),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should return empty list on RestTemplate error")
        void returnsEmptyOnError() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            List<TimeSeries> result = service.queryRange(
                    "test_query", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list on null response")
        void returnsEmptyOnNullResponse() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(null);

            List<TimeSeries> result = service.queryRange(
                    "test_query", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list on error status")
        void returnsEmptyOnErrorStatus() {
            String errorResponse = "{\"status\":\"error\",\"error\":\"parse error\"}";
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(errorResponse);

            List<TimeSeries> result = service.queryRange(
                    "test_query", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            assertThat(result).isEmpty();
        }
    }

    // ==================== Instant Query Tests ====================

    @Nested
    @DisplayName("queryInstant")
    class InstantQueryTests {

        @Test
        @DisplayName("Should parse vector response with single value")
        void parsesVectorResponse() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {"__name__": "emf_gateway_requests_active", "tenant": "tenant-1"},
                            "value": [1704067200, "5"]
                          }
                        ]
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryInstant("emf_gateway_requests_active{tenant=\"tenant-1\"}");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).labels()).containsEntry("tenant", "tenant-1");
            assertThat(result.get(0).dataPoints()).hasSize(1);
            assertThat(result.get(0).dataPoints().get(0).value()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Should construct correct URL for instant query")
        void constructsCorrectUrl() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}");

            service.queryInstant("test_query");

            verify(restTemplate).getForObject(
                    argThat((String url) -> url.contains("/api/v1/query")
                            && url.contains("query=")
                            && !url.contains("query_range")),
                    eq(String.class));
        }

        @Test
        @DisplayName("Should handle NaN values gracefully")
        void handlesNanValues() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {},
                            "value": [1704067200, "NaN"]
                          }
                        ]
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryInstant("test_query");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).dataPoints().get(0).value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return empty list on connection error")
        void returnsEmptyOnConnectionError() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            List<TimeSeries> result = service.queryInstant("test_query");

            assertThat(result).isEmpty();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty result array")
        void handlesEmptyResult() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "matrix",
                        "result": []
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryRange(
                    "test_query", Instant.ofEpochSecond(1704067200),
                    Instant.ofEpochSecond(1704067320), "60s");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle +Inf values")
        void handlesInfValues() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {},
                            "value": [1704067200, "+Inf"]
                          }
                        ]
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryInstant("test_query");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).dataPoints().get(0).value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should parse series with multiple labels")
        void handlesMultipleLabels() {
            String promResponse = """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "vector",
                        "result": [
                          {
                            "metric": {"tenant": "tenant-1", "status": "200", "route": "/api/users"},
                            "value": [1704067200, "100"]
                          }
                        ]
                      }
                    }
                    """;

            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(promResponse);

            List<TimeSeries> result = service.queryInstant("test_query");

            assertThat(result.get(0).labels()).hasSize(3);
            assertThat(result.get(0).labels()).containsEntry("tenant", "tenant-1");
            assertThat(result.get(0).labels()).containsEntry("status", "200");
            assertThat(result.get(0).labels()).containsEntry("route", "/api/users");
        }
    }
}
