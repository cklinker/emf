package io.kelta.worker.service.telehealth;

import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AutoArchiveSweep")
class AutoArchiveSweepTest {

    private static final String TENANT = "t1";

    private JdbcTemplate jdbcTemplate;
    private ArchiveService archiveService;
    private TenantQuotaResolver quotaResolver;
    private AutoArchiveSweep sweep;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        archiveService = mock(ArchiveService.class);
        quotaResolver = mock(TenantQuotaResolver.class);
        Map<String, Object> quotas = new LinkedHashMap<>();
        quotas.put(TenantTierQuotas.KEY_ARCHIVE_AFTER_DAYS, 30);
        when(quotaResolver.resolve(TENANT)).thenReturn(quotas);
        sweep = new AutoArchiveSweep(jdbcTemplate, archiveService, quotaResolver, true);
    }

    private void stubCandidate(Instant closedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "conv-1");
        row.put("tenantId", TENANT);
        row.put("closedAt", closedAt);
        when(jdbcTemplate.query(contains("FROM chat_conversation"), any(RowMapper.class), eq(100)))
                .thenReturn(List.of(row));
    }

    @Test
    @DisplayName("archives a CLOSED conversation past the tenant's archiveAfterDays window")
    void archivesDueConversation() {
        stubCandidate(Instant.now().minus(31, ChronoUnit.DAYS));

        sweep.sweep();

        verify(archiveService).archiveConversation(eq(TENANT), any(), eq("conv-1"));
    }

    @Test
    @DisplayName("skips a conversation still inside the archiveAfterDays window")
    void skipsNotYetDue() {
        stubCandidate(Instant.now().minus(5, ChronoUnit.DAYS));

        sweep.sweep();

        verify(archiveService, never()).archiveConversation(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("the candidate query claims CLOSED, unarchived rows with FOR UPDATE SKIP LOCKED")
    void candidateQueryClaimsSafely() {
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), eq(100))).thenReturn(List.of());

        sweep.findCandidates();

        String query = sql.getValue();
        assertThat(query).contains("c.status = 'CLOSED'");
        assertThat(query).contains("NOT EXISTS");
        assertThat(query).contains("FOR UPDATE OF c SKIP LOCKED");
    }

    @Test
    @DisplayName("disabled sweep does nothing")
    void disabledIsNoOp() {
        AutoArchiveSweep disabled = new AutoArchiveSweep(jdbcTemplate, archiveService, quotaResolver, false);
        disabled.sweep();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(archiveService, never()).archiveConversation(anyString(), any(), anyString());
    }
}
