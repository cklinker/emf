package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObservabilityQueryServiceTest {

    private RestClient tempoClient;
    private RestClient lokiClient;
    private RestClient mimirClient;
    private ObservabilityQueryService service;

    @BeforeEach
    void setUp() {
        tempoClient = mock(RestClient.class);
        lokiClient = mock(RestClient.class);
        mimirClient = mock(RestClient.class);
        service = new ObservabilityQueryService(tempoClient, lokiClient, mimirClient);
    }

    @Test
    void shouldReturnEmptyResultWhenTempoSearchFails() {
        var requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(tempoClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        var result = service.searchTraceSpans("tenant-1",
                Instant.now().minusSeconds(3600), Instant.now(),
                new HashMap<>(), 0, 50);

        assertEquals(0, result.totalHits());
        assertTrue(result.hits().isEmpty());
    }

    @Test
    void shouldParseTempoSearchResponse() {
        String mockResponse = """
                {
                  "traces": [{
                    "traceID": "abc123def456",
                    "rootServiceName": "emf-worker",
                    "rootTraceName": "GET /api/test",
                    "startTimeUnixNano": "1700000000000000000",
                    "durationMs": 42,
                    "spanSets": [{
                      "spans": [{
                        "spanID": "span123",
                        "startTimeUnixNano": "1700000000000000000",
                        "durationNanos": "42000000",
                        "attributes": [
                          {"key": "http.request.method", "value": {"stringValue": "GET"}},
                          {"key": "http.response.status_code", "value": {"intValue": "200"}}
                        ]
                      }]
                    }]
                  }]
                }
                """;

        mockTempoGet(mockResponse);

        var result = service.searchTraceSpans(null,
                Instant.ofEpochSecond(1700000000 - 3600),
                Instant.ofEpochSecond(1700000000 + 3600),
                new HashMap<>(), 0, 50);

        assertEquals(1, result.totalHits());
        Map<String, Object> hit = result.hits().getFirst();
        assertEquals("abc123def456", hit.get("traceID"));
        assertEquals("GET /api/test", hit.get("operationName"));
        assertEquals(42000L, hit.get("duration")); // 42ms → 42000μs

        @SuppressWarnings("unchecked")
        Map<String, Object> tagMap = (Map<String, Object>) hit.get("tagMap");
        assertNotNull(tagMap);
        assertEquals("GET", tagMap.get("http.request.method"));
        assertEquals(200L, tagMap.get("http.response.status_code"));
        assertEquals("emf-worker", tagMap.get("process.serviceName"));
    }

    @Test
    void shouldParseTempoTraceDetail() {
        String mockResponse = """
                {
                  "batches": [{
                    "resource": {
                      "attributes": [
                        {"key": "service.name", "value": {"stringValue": "emf-worker"}}
                      ]
                    },
                    "scopeSpans": [{
                      "spans": [
                        {
                          "traceId": "abc123",
                          "spanId": "span1",
                          "name": "GET /api/test",
                          "startTimeUnixNano": "1700000000000000000",
                          "endTimeUnixNano": "1700000000042000000",
                          "attributes": [
                            {"key": "http.request.method", "value": {"stringValue": "GET"}}
                          ],
                          "parentSpanId": ""
                        },
                        {
                          "traceId": "abc123",
                          "spanId": "span2",
                          "name": "SELECT users",
                          "startTimeUnixNano": "1700000000005000000",
                          "endTimeUnixNano": "1700000000010000000",
                          "attributes": [
                            {"key": "db.system", "value": {"stringValue": "postgresql"}}
                          ],
                          "parentSpanId": "span1"
                        }
                      ]
                    }]
                  }]
                }
                """;

        mockTempoGet(mockResponse);

        List<Map<String, Object>> spans = service.getTraceDetail("abc123");

        assertEquals(2, spans.size());

        Map<String, Object> rootSpan = spans.get(0);
        assertEquals("GET /api/test", rootSpan.get("operationName"));
        assertEquals(42000L, rootSpan.get("duration")); // 42ms in μs

        Map<String, Object> childSpan = spans.get(1);
        assertEquals("SELECT users", childSpan.get("operationName"));
        assertNotNull(childSpan.get("references"));
    }

    @Test
    void shouldReturnEmptyResultWhenTempoTraceDetailFails() {
        var requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(tempoClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), any(Object.class))).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Not found"));

        List<Map<String, Object>> result = service.getTraceDetail("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldParseLokiStreamResponse() {
        String mockResponse = """
                {
                  "data": {
                    "resultType": "streams",
                    "result": [{
                      "stream": {"namespace": "emf", "app": "emf-worker"},
                      "values": [
                        ["1700000000000000000", "{\\"level\\":\\"INFO\\",\\"message\\":\\"Request processed\\",\\"logger_name\\":\\"io.kelta.worker\\",\\"thread_name\\":\\"main\\",\\"traceId\\":\\"trace-abc\\"}"],
                        ["1699999999000000000", "plain text log line"]
                      ]
                    }]
                  }
                }
                """;

        mockLokiGet(mockResponse);

        Map<String, String> filters = new HashMap<>();
        filters.put("@timestamp_gte", "2023-11-14T00:00:00Z");
        filters.put("@timestamp_lte", "2023-11-15T00:00:00Z");

        var result = service.searchLogs(filters, 0, 50);

        assertEquals(2, result.hits().size());

        Map<String, Object> structuredLog = result.hits().get(0);
        assertEquals("INFO", structuredLog.get("level"));
        assertEquals("Request processed", structuredLog.get("message"));
        assertEquals("trace-abc", structuredLog.get("traceId"));
        assertEquals("emf-worker", structuredLog.get("service"));

        Map<String, Object> plainLog = result.hits().get(1);
        assertEquals("plain text log line", plainLog.get("message"));
    }

    @Test
    void shouldParseMimirInstantQueryResponse() {
        String mockResponse = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [{
                      "metric": {},
                      "value": [1700000000, "42.5"]
                    }]
                  }
                }
                """;

        mockMimirGet(mockResponse);

        Instant end = Instant.ofEpochSecond(1700000000);
        Instant start = end.minusSeconds(86400);

        Map<String, Object> summary = service.getMetricsSummary(null, start, end);

        assertNotNull(summary);
        // All queries return the same mock, so totalRequests will be 43 (rounded from 42.5)
        assertNotNull(summary.get("totalRequests"));
    }

    @Test
    void shouldParseMimirRangeQueryResponse() {
        String mockResponse = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [{
                      "metric": {},
                      "values": [
                        [1700000000, "10"],
                        [1700000060, "15"],
                        [1700000120, "8"]
                      ]
                    }]
                  }
                }
                """;

        mockMimirGet(mockResponse);

        Instant start = Instant.ofEpochSecond(1700000000);
        Instant end = Instant.ofEpochSecond(1700000120);

        List<Map<String, Object>> result = service.getRequestCountOverTime(null, start, end, "60s");

        assertEquals(3, result.size());
        assertEquals(10L, result.get(0).get("value"));
        assertEquals(15L, result.get(1).get("value"));
        assertEquals(8L, result.get(2).get("value"));
    }

    @Test
    void shouldHandleEmptyMimirResponse() {
        String mockResponse = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": []
                  }
                }
                """;

        mockMimirGet(mockResponse);

        Instant end = Instant.now();
        Instant start = end.minusSeconds(3600);

        Map<String, Double> percentiles = service.getLatencyPercentiles(null, start, end);

        assertEquals(0.0, percentiles.get("p50"));
        assertEquals(0.0, percentiles.get("p95"));
        assertEquals(0.0, percentiles.get("p99"));
        assertEquals(0.0, percentiles.get("avg"));
    }

    @Test
    void shouldHandleTraceIdFilterByCallingTraceDetail() {
        String mockTraceResponse = """
                {
                  "batches": [{
                    "resource": {
                      "attributes": [
                        {"key": "service.name", "value": {"stringValue": "emf-worker"}}
                      ]
                    },
                    "scopeSpans": [{
                      "spans": [{
                        "traceId": "trace-direct",
                        "spanId": "span-direct",
                        "name": "GET /api/direct",
                        "startTimeUnixNano": "1700000000000000000",
                        "endTimeUnixNano": "1700000000050000000",
                        "attributes": [
                          {"key": "span.kind", "value": {"stringValue": "server"}},
                          {"key": "http.request.method", "value": {"stringValue": "GET"}}
                        ],
                        "parentSpanId": ""
                      }]
                    }]
                  }]
                }
                """;

        mockTempoGet(mockTraceResponse);

        Map<String, String> filters = new HashMap<>();
        filters.put("traceId", "trace-direct");

        var result = service.searchTraceSpans(null,
                Instant.ofEpochSecond(1700000000 - 3600),
                Instant.ofEpochSecond(1700000000 + 3600),
                filters, 0, 50);

        assertFalse(result.hits().isEmpty());
    }

    // --- Mock helpers ---

    private void mockTempoGet(String responseBody) {
        var requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(tempoClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), any(Object.class))).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);
    }

    private void mockLokiGet(String responseBody) {
        var requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(lokiClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);
    }

    private void mockMimirGet(String responseBody) {
        var requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(mimirClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString())).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);
    }
}
