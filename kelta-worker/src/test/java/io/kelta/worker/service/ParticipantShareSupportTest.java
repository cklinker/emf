package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ParticipantShareSupport")
class ParticipantShareSupportTest {

    private JdbcTemplate jdbcTemplate;
    private ParticipantShareSupport support;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        support = new ParticipantShareSupport(jdbcTemplate);
    }

    @Test
    @DisplayName("grant writes a USER record_share with the participant reason")
    void grantWritesShare() {
        when(jdbcTemplate.queryForList(contains("FROM collection"), eq(String.class), eq("appointments")))
                .thenReturn(List.of("col-1"));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);

        TenantContext.runWithTenant("tenant-1", "acme", () -> {
            boolean granted = support.grant("appointments", "rec-1", "user-1", "READ");
            assertThat(granted).isTrue();
        });

        verify(jdbcTemplate).update(contains("INSERT INTO record_share"),
                anyString(), eq("tenant-1"), eq("col-1"), eq("rec-1"), eq("user-1"), eq("READ"),
                eq("col-1"), eq("rec-1"), eq("user-1"), eq("READ"));
    }

    @Test
    @DisplayName("grant is idempotent — an existing equivalent share is a no-op")
    void grantIdempotent() {
        when(jdbcTemplate.queryForList(contains("FROM collection"), eq(String.class), anyString()))
                .thenReturn(List.of("col-1"));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(0);

        TenantContext.runWithTenant("tenant-1", "acme", () ->
                assertThat(support.grant("appointments", "rec-1", "user-1", "READ")).isFalse());
    }

    @Test
    @DisplayName("grant skips unknown collections and missing tenant context")
    void grantGuards() {
        // No tenant context bound at all:
        assertThat(support.grant("appointments", "rec-1", "user-1", "READ")).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));

        // Unknown collection:
        when(jdbcTemplate.queryForList(contains("FROM collection"), eq(String.class), anyString()))
                .thenReturn(List.of());
        TenantContext.runWithTenant("tenant-1", "acme", () ->
                assertThat(support.grant("nope", "rec-1", "user-1", "READ")).isFalse());
    }

    @Test
    @DisplayName("revoke deletes only participant-reason shares")
    void revokeDeletesParticipantShares() {
        when(jdbcTemplate.queryForList(contains("FROM collection"), eq(String.class), anyString()))
                .thenReturn(List.of("col-1"));
        when(jdbcTemplate.update(contains("DELETE FROM record_share"),
                eq("col-1"), eq("rec-1"), eq("user-1"))).thenReturn(1);

        assertThat(support.revoke("appointments", "rec-1", "user-1")).isTrue();
        verify(jdbcTemplate).update(contains("reason = 'participant'"),
                eq("col-1"), eq("rec-1"), eq("user-1"));
    }
}
