package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AuditQueryService(jdbcTemplate);
    }

    @Test
    void shouldQuerySetupAuditWhenTypeIsSetup() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("setup_audit_trail"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "1", "action", "CREATED")));

        var result = service.searchAudit("tenant-1", "setup", null, null,
                null, null, 0, 50);

        assertEquals(1, result.totalHits());
        assertEquals(1, result.hits().size());
        verify(jdbcTemplate).queryForList(contains("setup_audit_trail"), any(Object[].class));
    }

    @Test
    void shouldQuerySecurityAuditWhenTypeIsSecurity() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(2L);
        when(jdbcTemplate.queryForList(contains("security_audit_log"), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("id", "1", "event_type", "LOGIN_SUCCESS"),
                        Map.of("id", "2", "event_type", "PERMISSION_CHANGED")));

        var result = service.searchAudit("tenant-1", "security", null, null,
                null, null, 0, 50);

        assertEquals(2, result.totalHits());
        assertEquals(2, result.hits().size());
    }

    @Test
    void shouldFilterByTenantAndTimeRange() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-02T00:00:00Z");

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        service.searchAudit("tenant-1", "setup", "CREATED", "user-1",
                start, end, 0, 50);

        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("setup_audit_trail")
                        && sql.contains("tenant_id")
                        && sql.contains("timestamp >=")),
                any(Object[].class));
    }

    @Test
    void shouldQueryBothTablesWhenNoAuditTypeSpecified() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "1", "@timestamp", java.sql.Timestamp.from(Instant.now()))));

        var result = service.searchAudit("tenant-1", null, null, null,
                null, null, 0, 50);

        // Should query both tables
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }
}
