package io.kelta.worker.service.telehealth;

import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.SecurityAuditLogger;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retention purge sweep (telehealth slice 7) — <b>DESTRUCTIVE</b>. Two
 * concerns, both gated by the same dry-run flag:
 *
 * <ol>
 *   <li><b>Expired-archive purge.</b> Archives past {@code retention_until},
 *       NOT on {@code legal_hold}, NOT already purged, are purged: their
 *       artifact objects and any linked recording object are deleted from S3
 *       and the row is stamped {@code purged_at} (a tombstone — the archive row
 *       itself is NEVER hard-deleted, preserving the immutable audit record of
 *       what existed and when it was destroyed).</li>
 *   <li><b>Live-message purge.</b> {@code chat_message} rows for conversations
 *       archived more than {@code purgeLiveAfterDays} ago are deleted — the
 *       transcript is already preserved in the artifact and the message
 *       attachments were re-parented to the archive row at archive time, so
 *       nothing referenced by the transcript is lost.</li>
 * </ol>
 *
 * <p><b>Safety design.</b> Dry-run is the DEFAULT
 * ({@code kelta.telehealth.retention.purge-dry-run:true}). In dry-run the sweep
 * only LOGS what it WOULD purge — no S3 delete, no {@code purged_at} stamp, no
 * message deletion. Only when the property is explicitly {@code false} does it
 * destroy anything. A row on {@code legal_hold} is excluded by the query AND
 * re-checked per row before any deletion, so a legal hold set between the query
 * and the delete still wins. Every run logs a clear "due / purged (or would
 * purge)" summary; every real purge is audited as {@code ARCHIVE_PURGED}.
 */
@Service
public class RetentionPurgeSweep {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeSweep.class);
    private static final int BATCH_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final S3StorageService storageService;
    private final TenantQuotaResolver quotaResolver;
    private final boolean enabled;
    private final boolean dryRun;

    public RetentionPurgeSweep(JdbcTemplate jdbcTemplate,
                               S3StorageService storageService,
                               TenantQuotaResolver quotaResolver,
                               @Value("${kelta.telehealth.retention.enabled:true}") boolean enabled,
                               @Value("${kelta.telehealth.retention.purge-dry-run:true}") boolean dryRun) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
        this.quotaResolver = quotaResolver;
        this.enabled = enabled;
        this.dryRun = dryRun;
    }

    @Scheduled(fixedDelayString = "${kelta.telehealth.retention.poll-interval-ms:3600000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            purgeExpiredArchives();
        } catch (Exception e) {
            log.error("Retention purge (archives) failed: {}", e.getMessage(), e);
        }
        try {
            purgeLiveMessages();
        } catch (Exception e) {
            log.error("Retention purge (live messages) failed: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- Archive purge

    void purgeExpiredArchives() {
        List<Map<String, Object>> due = findDueArchives();
        int purged = 0;
        for (Map<String, Object> archive : due) {
            String id = String.valueOf(archive.get("id"));
            String tenantId = String.valueOf(archive.get("tenantId"));

            // Belt-and-suspenders: never purge a legal-hold row, even if the row
            // was placed on hold after the claim query read it.
            if (isLegalHold(id, tenantId)) {
                log.info("Retention: skipping archive {} (tenant {}) — legal hold", id, tenantId);
                continue;
            }

            List<String> storageKeys = artifactStorageKeys(id, tenantId);
            String recordingKey = recordingKeyFor(id, tenantId);
            if (recordingKey != null && !recordingKey.isBlank()) {
                storageKeys.add(recordingKey);
            }

            if (dryRun) {
                log.info("Retention DRY-RUN: WOULD purge archive {} (tenant {}) — {} object(s): {}",
                        id, tenantId, storageKeys.size(), storageKeys);
                continue;
            }

            // Real purge: delete objects first (best-effort per object), then
            // tombstone. The row is kept forever as proof of destruction.
            for (String key : storageKeys) {
                deleteObjectQuietly(key);
            }
            int stamped = jdbcTemplate.update(
                    "UPDATE telehealth_archive SET purged_at = NOW(), updated_at = NOW() "
                            + "WHERE id = ? AND tenant_id = ? AND purged_at IS NULL AND NOT legal_hold",
                    id, tenantId);
            if (stamped > 0) {
                purged++;
                SecurityAuditLogger.log(SecurityAuditLogger.EventType.ARCHIVE_PURGED, "system",
                        id, tenantId, "success", "objectsDeleted=" + storageKeys.size());
            }
        }
        if (!due.isEmpty()) {
            log.info("Retention archive sweep: {} due, {} {}", due.size(),
                    dryRun ? due.size() : purged, dryRun ? "would purge (dry-run)" : "purged");
        }
    }

    /**
     * Expired archives to purge: past retention, not on legal hold, not already
     * purged. {@code FOR UPDATE SKIP LOCKED} so concurrent pods take disjoint
     * slices and never double-purge a row.
     */
    List<Map<String, Object>> findDueArchives() {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id
                FROM telehealth_archive
                WHERE retention_until IS NOT NULL
                  AND retention_until <= NOW()
                  AND NOT legal_hold
                  AND purged_at IS NULL
                ORDER BY retention_until ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("tenantId", rs.getString("tenant_id"));
                    return row;
                },
                BATCH_LIMIT);
    }

    // ------------------------------------------------------------- Live-message purge

    void purgeLiveMessages() {
        List<Map<String, Object>> due = findConversationsForLivePurge();
        int purgedConversations = 0;
        long purgedMessages = 0;
        for (Map<String, Object> row : due) {
            String conversationId = String.valueOf(row.get("conversationId"));
            String tenantId = String.valueOf(row.get("tenantId"));

            Long liveCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_message WHERE tenant_id = ? AND conversation_id = ?",
                    Long.class, tenantId, conversationId);
            long count = liveCount == null ? 0 : liveCount;
            if (count == 0) {
                continue;
            }
            if (dryRun) {
                log.info("Retention DRY-RUN: WOULD purge {} live message(s) for archived conversation "
                        + "{} (tenant {})", count, conversationId, tenantId);
                continue;
            }
            int deleted = jdbcTemplate.update(
                    "DELETE FROM chat_message WHERE tenant_id = ? AND conversation_id = ?",
                    tenantId, conversationId);
            if (deleted > 0) {
                purgedConversations++;
                purgedMessages += deleted;
            }
        }
        if (!due.isEmpty()) {
            log.info("Retention live-message sweep: {} archived conversation(s) eligible, "
                            + "{} {} ({} message(s))", due.size(),
                    dryRun ? due.size() : purgedConversations,
                    dryRun ? "would purge (dry-run)" : "purged",
                    dryRun ? "estimated" : purgedMessages);
        }
    }

    /**
     * Archived conversations still holding live message rows, filtered to those
     * archived at least the owning tenant's {@code purgeLiveAfterDays} ago. Not
     * on legal hold, not purged. The SQL returns all live archived conversations
     * (oldest first, capped); the per-tenant age window — which a single SQL
     * cutoff can't express — is applied per row in Java below.
     */
    List<Map<String, Object>> findConversationsForLivePurge() {
        List<Map<String, Object>> archived = jdbcTemplate.query(
                """
                SELECT a.tenant_id, a.source_id AS conversation_id, a.archived_at
                FROM telehealth_archive a
                WHERE a.source_type = 'CONVERSATION'
                  AND a.archived_at IS NOT NULL
                  AND NOT a.legal_hold
                  AND a.purged_at IS NULL
                ORDER BY a.archived_at ASC
                LIMIT ?
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tenantId", rs.getString("tenant_id"));
                    row.put("conversationId", rs.getString("conversation_id"));
                    java.sql.Timestamp archivedAt = rs.getTimestamp("archived_at");
                    row.put("archivedAt", archivedAt == null ? null : archivedAt.toInstant());
                    return row;
                },
                BATCH_LIMIT);

        List<Map<String, Object>> due = new ArrayList<>();
        java.time.Instant now = java.time.Instant.now();
        for (Map<String, Object> row : archived) {
            String tenantId = String.valueOf(row.get("tenantId"));
            java.time.Instant archivedAt = (java.time.Instant) row.get("archivedAt");
            int purgeLiveAfterDays = intSetting(tenantId, TenantTierQuotas.KEY_PURGE_LIVE_AFTER_DAYS, 90);
            if (archivedAt != null
                    && archivedAt.isBefore(now.minus(java.time.Duration.ofDays(purgeLiveAfterDays)))) {
                due.add(row);
            }
        }
        return due;
    }

    // ------------------------------------------------------------- Helpers

    private boolean isLegalHold(String id, String tenantId) {
        List<Boolean> holds = jdbcTemplate.queryForList(
                "SELECT legal_hold FROM telehealth_archive WHERE id = ? AND tenant_id = ?",
                Boolean.class, id, tenantId);
        return !holds.isEmpty() && Boolean.TRUE.equals(holds.get(0));
    }

    private List<String> artifactStorageKeys(String archiveId, String tenantId) {
        return new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT storage_key FROM file_attachment WHERE tenant_id = ? "
                        + "AND collection_id = 'telehealth-archives' AND record_id = ? "
                        + "AND storage_key IS NOT NULL AND storage_key <> ''",
                String.class, tenantId, archiveId));
    }

    private String recordingKeyFor(String archiveId, String tenantId) {
        List<String> keys = jdbcTemplate.queryForList(
                """
                SELECT vs.recording_key
                FROM telehealth_archive a
                JOIN video_session vs ON vs.id = a.source_id AND vs.tenant_id = a.tenant_id
                WHERE a.id = ? AND a.tenant_id = ? AND a.source_type = 'VIDEO_SESSION'
                  AND vs.recording_key IS NOT NULL AND vs.recording_key <> ''
                """,
                String.class, archiveId, tenantId);
        return keys.isEmpty() ? null : keys.get(0);
    }

    private void deleteObjectQuietly(String storageKey) {
        if (!storageService.isEnabled() || storageKey == null || storageKey.isBlank()) {
            return;
        }
        // A recording key may be a full s3:// URI (from LiveKit egress); only the
        // object path is a valid delete key. Strip the scheme+bucket if present.
        String key = normalizeKey(storageKey);
        try {
            storageService.deleteObject(key);
        } catch (Exception e) {
            log.warn("Retention purge: failed to delete S3 object '{}': {}", key, e.getMessage());
        }
    }

    /** LiveKit egress may store an {@code s3://bucket/path} URI; delete needs the path. */
    static String normalizeKey(String storageKey) {
        if (storageKey.startsWith("s3://")) {
            String withoutScheme = storageKey.substring("s3://".length());
            int slash = withoutScheme.indexOf('/');
            return slash >= 0 ? withoutScheme.substring(slash + 1) : withoutScheme;
        }
        return storageKey;
    }

    private int intSetting(String tenantId, String key, int fallback) {
        Object value = quotaResolver.resolve(tenantId).get(key);
        if (value instanceof Number num) {
            int i = num.intValue();
            return i == Integer.MAX_VALUE ? fallback : i;
        }
        return fallback;
    }
}
