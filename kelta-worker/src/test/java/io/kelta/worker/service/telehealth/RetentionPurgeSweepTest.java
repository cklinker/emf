package io.kelta.worker.service.telehealth;

import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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

@DisplayName("RetentionPurgeSweep")
class RetentionPurgeSweepTest {

    private static final String TENANT = "t1";

    private JdbcTemplate jdbcTemplate;
    private S3StorageService storageService;
    private TenantQuotaResolver quotaResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        storageService = mock(S3StorageService.class);
        when(storageService.isEnabled()).thenReturn(true);
        quotaResolver = mock(TenantQuotaResolver.class);
        Map<String, Object> quotas = new LinkedHashMap<>();
        quotas.put(TenantTierQuotas.KEY_PURGE_LIVE_AFTER_DAYS, 90);
        when(quotaResolver.resolve(TENANT)).thenReturn(quotas);
        // No conversations eligible for live-message purge unless a test stubs it.
        when(jdbcTemplate.query(contains("source_type = 'CONVERSATION'"), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenReturn(List.of());
    }

    private RetentionPurgeSweep sweep(boolean dryRun) {
        return new RetentionPurgeSweep(jdbcTemplate, storageService, quotaResolver, true, dryRun);
    }

    private void stubDueArchive(String id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("tenantId", TENANT);
        when(jdbcTemplate.query(contains("FROM telehealth_archive"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(100)))
                .thenReturn(List.of(row));
        // Not on legal hold when re-checked.
        when(jdbcTemplate.queryForList(contains("SELECT legal_hold"), eq(Boolean.class), eq(id), eq(TENANT)))
                .thenReturn(List.of(Boolean.FALSE));
        // One artifact object; no recording.
        when(jdbcTemplate.queryForList(contains("SELECT storage_key FROM file_attachment"),
                eq(String.class), eq(TENANT), eq(id)))
                .thenReturn(List.of("t1/telehealth-archives/" + id + "/a/rec.json"));
        when(jdbcTemplate.queryForList(contains("recording_key"), eq(String.class), eq(id), eq(TENANT)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("DRY-RUN (default) deletes nothing and stamps nothing")
    void dryRunPurgesNothing() {
        stubDueArchive("arch-1");

        sweep(true).purgeExpiredArchives();

        verify(storageService, never()).deleteObject(anyString());
        verify(jdbcTemplate, never()).update(contains("SET purged_at"), any(), any());
    }

    @Test
    @DisplayName("real purge deletes artifact objects and stamps purged_at (tombstone kept)")
    void realPurgeDeletesAndTombstones() {
        stubDueArchive("arch-1");
        when(jdbcTemplate.update(contains("SET purged_at"), eq("arch-1"), eq(TENANT))).thenReturn(1);

        sweep(false).purgeExpiredArchives();

        verify(storageService).deleteObject("t1/telehealth-archives/arch-1/a/rec.json");
        verify(jdbcTemplate).update(contains("SET purged_at"), eq("arch-1"), eq(TENANT));
        // Tombstone: the archive row is never hard-deleted.
        verify(jdbcTemplate, never()).update(contains("DELETE FROM telehealth_archive"), (Object[]) any());
    }

    @Test
    @DisplayName("a legal-hold row that slips into the batch is skipped before any deletion")
    void legalHoldSkippedEvenIfClaimed() {
        stubDueArchive("arch-hold");
        // Re-check now reports the row IS on hold (set after the claim query read it).
        when(jdbcTemplate.queryForList(contains("SELECT legal_hold"), eq(Boolean.class),
                eq("arch-hold"), eq(TENANT))).thenReturn(List.of(Boolean.TRUE));

        sweep(false).purgeExpiredArchives();

        verify(storageService, never()).deleteObject(anyString());
        verify(jdbcTemplate, never()).update(contains("SET purged_at"), any(), any());
    }

    @Test
    @DisplayName("the due-archives claim query filters legal_hold, purged, and un-expired rows in SQL")
    void dueQueryHasSafetyPredicates() {
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), eq(100)))
                .thenReturn(List.of());

        sweep(false).findDueArchives();

        String query = sql.getValue();
        assertThat(query).contains("retention_until <= NOW()");
        assertThat(query).contains("NOT legal_hold");
        assertThat(query).contains("purged_at IS NULL");
        assertThat(query).contains("FOR UPDATE SKIP LOCKED");
    }

    @Test
    @DisplayName("normalizeKey strips an s3:// scheme+bucket to the object path, passes plain keys through")
    void normalizeKeyStripsScheme() {
        assertThat(RetentionPurgeSweep.normalizeKey("s3://my-bucket/t1/recordings/rec.mp4"))
                .isEqualTo("t1/recordings/rec.mp4");
        assertThat(RetentionPurgeSweep.normalizeKey("t1/telehealth-archives/a/b/c.json"))
                .isEqualTo("t1/telehealth-archives/a/b/c.json");
    }
}
