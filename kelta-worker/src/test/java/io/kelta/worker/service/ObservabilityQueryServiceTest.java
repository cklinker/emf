package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObservabilityQueryServiceTest {

    private RestClient restClient;
    private JdbcTemplate jdbcTemplate;
    private ObservabilityQueryService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ObservabilityQueryService(restClient, jdbcTemplate);
    }

    @Test
    void shouldReturnEmptyResultWhenOpenSearchFails() {
        // When RestClient throws, service should return empty results not crash
        var requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), any(Object.class))).thenReturn(requestSpec);
        when(requestSpec.contentType(any())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
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
    void shouldQueryPostgresForTraceRequestData() {
        List<Map<String, Object>> expected = List.of(
                Map.of("trace_id", "abc", "span_id", "def", "method", "GET"));
        when(jdbcTemplate.queryForList(contains("request_data"), eq("trace-123")))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getTraceRequestData("trace-123");

        assertEquals(1, result.size());
        assertEquals("abc", result.getFirst().get("trace_id"));
    }

    @Test
    void shouldFlattenJaegerTags() {
        // Test the tag flattening logic via searchTraceSpans
        // This exercises the flattenTags method indirectly
        String mockResponse = """
                {
                  "hits": {
                    "total": {"value": 1},
                    "hits": [{
                      "_id": "span-1",
                      "_source": {
                        "traceID": "trace-1",
                        "spanID": "span-1",
                        "operationName": "GET /api/test",
                        "startTimeMillis": 1700000000000,
                        "duration": 5000,
                        "tags": [
                          {"key": "http.request.method", "type": "string", "value": "GET"},
                          {"key": "http.response.status_code", "type": "string", "value": "200"},
                          {"key": "otel.library.version", "type": "int64", "value": 42}
                        ],
                        "tag.span@kind": "server",
                        "process": {
                          "serviceName": "kelta-worker",
                          "tags": [{"key": "deployment.environment", "value": "test"}]
                        }
                      }
                    }]
                  }
                }
                """;

        var requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), any(Object.class))).thenReturn(requestSpec);
        when(requestSpec.contentType(any())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(mockResponse);

        var result = service.searchTraceSpans(null,
                Instant.ofEpochMilli(1700000000000L - 3600000),
                Instant.ofEpochMilli(1700000000000L + 3600000),
                new HashMap<>(), 0, 50);

        assertEquals(1, result.totalHits());
        Map<String, Object> hit = result.hits().getFirst();
        assertEquals("trace-1", hit.get("traceID"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tagMap = (Map<String, Object>) hit.get("tagMap");
        assertNotNull(tagMap);
        assertEquals("GET", tagMap.get("http.request.method"));
        assertEquals("200", tagMap.get("http.response.status_code"));
        assertEquals(42L, tagMap.get("otel.library.version"));
        assertEquals("kelta-worker", tagMap.get("process.serviceName"));
        assertEquals("test", tagMap.get("process.deployment.environment"));
    }
}
