package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestDataCaptureServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RequestDataCaptureService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new RequestDataCaptureService(jdbcTemplate);
    }

    @Test
    void shouldInsertRequestDataToPostgres() {
        Map<String, String> reqHeaders = Map.of("Content-Type", "application/json");
        Map<String, String> respHeaders = Map.of("Content-Type", "application/json");

        service.captureRequestData("trace-123", "span-456",
                reqHeaders, respHeaders,
                "{\"name\":\"test\"}", "{\"id\":\"1\"}",
                "POST", "/api/accounts", 201,
                "tenant-1", "user-1", "user@example.com", "corr-789");

        verify(jdbcTemplate).update(
                contains("INSERT INTO request_data"),
                any(String.class),      // id
                eq("tenant-1"),         // tenant_id
                eq("trace-123"),        // trace_id
                eq("span-456"),         // span_id
                eq("POST"),             // method
                eq("/api/accounts"),    // path
                eq(201),                // status_code
                eq("user-1"),           // user_id
                eq("user@example.com"), // user_email
                eq("corr-789"),         // correlation_id
                any(String.class),      // request_headers JSON
                any(String.class),      // response_headers JSON
                eq("{\"name\":\"test\"}"), // request_body
                eq("{\"id\":\"1\"}")     // response_body
        );
    }

    @Test
    void shouldHandleNullHeaders() {
        service.captureRequestData("trace-123", "span-456",
                null, null, null, null,
                "GET", "/api/health", 200,
                "tenant-1", null, null, null);

        verify(jdbcTemplate).update(
                contains("INSERT INTO request_data"),
                any(String.class), eq("tenant-1"), eq("trace-123"), eq("span-456"),
                eq("GET"), eq("/api/health"), eq(200),
                isNull(), isNull(), isNull(),
                isNull(), isNull(),
                isNull(), isNull()
        );
    }

    @Test
    void shouldCatchExceptionWithoutThrowing() {
        doThrow(new RuntimeException("Connection refused"))
                .when(jdbcTemplate).update(anyString(),
                        any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any(), any());

        // Should NOT throw
        service.captureRequestData("trace-123", "span-456",
                Map.of(), Map.of(), null, null,
                "GET", "/api/test", 200,
                "tenant-1", null, null, null);
    }
}
