package io.kelta.worker.controller;

import io.kelta.worker.service.AuditQueryService;
import io.kelta.worker.service.ObservabilityQueryService;
import io.kelta.worker.service.ObservabilityQueryService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ObservabilityController Tests")
class ObservabilityControllerTest {

    private ObservabilityQueryService queryService;
    private AuditQueryService auditQueryService;
    private ObservabilityController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(ObservabilityQueryService.class);
        auditQueryService = mock(AuditQueryService.class);
        controller = new ObservabilityController(queryService, auditQueryService);
    }

    @Nested
    @DisplayName("searchRequestLogs")
    class SearchRequestLogs {

        @Test
        void shouldReturnResultsWithDefaults() {
            when(queryService.searchTraceSpans(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new SearchResult(List.of(), 0));

            var response = controller.searchRequestLogs("slug", null, null, null, null, null, null, null, 0, 50);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void shouldReturn500OnException() {
            when(queryService.searchTraceSpans(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Tempo error"));

            var response = controller.searchRequestLogs("slug", null, null, null, null, null, null, null, 0, 50);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getRequestLog")
    class GetRequestLog {

        @Test
        void shouldReturnTraceDetail() {
            when(queryService.getTraceDetail("trace-123")).thenReturn(List.of(Map.of("span", "data")));

            var response = controller.getRequestLog("trace-123");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void shouldReturn500OnException() {
            when(queryService.getTraceDetail(any())).thenThrow(new RuntimeException("error"));

            var response = controller.getRequestLog("trace-123");
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("searchLogs")
    class SearchLogs {

        @Test
        void shouldReturnLogResults() {
            when(queryService.searchLogs(any(), anyInt(), anyInt()))
                    .thenReturn(new SearchResult(List.of(), 0));

            var response = controller.searchLogs(null, null, null, null, null, null, 0, 50);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("searchAudit")
    class SearchAudit {

        @Test
        void shouldReturnAuditResults() {
            when(auditQueryService.searchAudit(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new SearchResult(List.of(), 0));

            var response = controller.searchAudit("tenant-1", null, null, null, null, null, 0, 50);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void shouldReturn500OnException() {
            when(auditQueryService.searchAudit(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB error"));

            var response = controller.searchAudit("tenant-1", null, null, null, null, null, 0, 50);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }
}
