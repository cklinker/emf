package io.kelta.worker.service.telehealth;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-archive sweep (telehealth slice 7): every poll it finds CLOSED
 * conversations whose {@code closed_at} is older than the owning tenant's
 * {@code archiveAfterDays} setting and that have no archive row yet, then
 * archives each via {@link ArchiveService} (which flips the conversation to
 * {@code ARCHIVED} — the durable "done" marker that keeps it out of the next
 * cycle) under the row's tenant scope.
 *
 * <p>Multi-pod safe without leader election: candidate rows are read with
 * {@code FOR UPDATE SKIP LOCKED}, so two pods never claim the same conversation
 * in a cycle, and {@link ArchiveService#archiveConversation} is idempotent
 * (unique archive index + existing-row short-circuit) as a second line of
 * defense. A pod crash mid-archive simply leaves the row CLOSED for the next
 * cycle — archive-then-mark means no half-archived state is ever observed.
 */
@Service
public class AutoArchiveSweep {

    private static final Logger log = LoggerFactory.getLogger(AutoArchiveSweep.class);
    private static final int BATCH_LIMIT = 100;
    private static final ChatService.ChatActor SYSTEM_ACTOR =
            new ChatService.ChatActor("system", "system", "INTERNAL");

    private final JdbcTemplate jdbcTemplate;
    private final ArchiveService archiveService;
    private final TenantQuotaResolver quotaResolver;
    private final boolean enabled;

    public AutoArchiveSweep(JdbcTemplate jdbcTemplate,
                            ArchiveService archiveService,
                            TenantQuotaResolver quotaResolver,
                            @Value("${kelta.telehealth.auto-archive.enabled:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.archiveService = archiveService;
        this.quotaResolver = quotaResolver;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${kelta.telehealth.auto-archive.poll-interval-ms:60000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            List<Map<String, Object>> candidates = findCandidates();
            int archived = 0;
            for (Map<String, Object> candidate : candidates) {
                String tenantId = String.valueOf(candidate.get("tenantId"));
                String conversationId = String.valueOf(candidate.get("id"));
                Instant closedAt = (Instant) candidate.get("closedAt");

                int archiveAfterDays = intSetting(tenantId, TenantTierQuotas.KEY_ARCHIVE_AFTER_DAYS, 30);
                if (closedAt == null
                        || closedAt.isAfter(Instant.now().minus(Duration.ofDays(archiveAfterDays)))) {
                    continue; // not yet due under this tenant's setting
                }
                try {
                    TenantContext.runWithTenant(tenantId,
                            () -> archiveService.archiveConversation(tenantId, SYSTEM_ACTOR, conversationId));
                    archived++;
                } catch (Exception e) {
                    log.warn("Auto-archive failed for conversation {} (tenant {}): {}",
                            conversationId, tenantId, e.getMessage());
                }
            }
            if (archived > 0) {
                log.info("Auto-archived {} conversation(s)", archived);
            }
        } catch (Exception e) {
            log.error("Auto-archive sweep failed: {}", e.getMessage(), e);
        }
    }

    /**
     * CLOSED, not-yet-archived conversations, oldest first. {@code FOR UPDATE
     * SKIP LOCKED} lets concurrent pods take disjoint slices; the per-tenant
     * {@code archiveAfterDays} due check is applied per row in {@link #sweep}
     * (a single SQL cutoff can't express per-tenant windows).
     */
    List<Map<String, Object>> findCandidates() {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.tenant_id, c.closed_at
                FROM chat_conversation c
                WHERE c.status = 'CLOSED'
                  AND c.closed_at IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM telehealth_archive a
                      WHERE a.tenant_id = c.tenant_id
                        AND a.source_type = 'CONVERSATION'
                        AND a.source_id = c.id
                  )
                ORDER BY c.closed_at ASC
                LIMIT ?
                FOR UPDATE OF c SKIP LOCKED
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("tenantId", rs.getString("tenant_id"));
                    Timestamp closedAt = rs.getTimestamp("closed_at");
                    row.put("closedAt", closedAt == null ? null : closedAt.toInstant());
                    return row;
                },
                BATCH_LIMIT);
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
