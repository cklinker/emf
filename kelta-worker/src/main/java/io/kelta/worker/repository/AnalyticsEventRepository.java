package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes and prunes the {@code analytics_event} capture table (consumer-alerting slice 8).
 *
 * <p><b>Two callers, two RLS modes, both correct via {@code TenantAwareDataSourceConfig}.</b>
 * The capture inserts run on request threads with a bound {@code TenantContext}, so the
 * connection carries {@code SET LOCAL app.current_tenant_id = <tenant>} and the row's explicit
 * {@code tenant_id} passes the {@code tenant_isolation} WITH CHECK. The retention delete runs on
 * the scheduler thread with no tenant bound, so the connection carries session
 * {@code app.current_tenant_id = ''} and the {@code admin_bypass} policy lets it prune across
 * every tenant — the same mechanism {@link FlowLogRetentionRepository} relies on.
 *
 * <p>Hand-written SQL on {@link JdbcTemplate} — no JPA.
 */
@Repository
public class AnalyticsEventRepository {

    private final JdbcTemplate jdbc;

    public AnalyticsEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One capture row ready for insert. {@code utmJson}/{@code metadataJson} are pre-serialized
     * JSON strings (or null → defaulted to {@code {}}); {@code occurredAt} is already clamped
     * not-future by the caller.
     */
    public record AnalyticsEvent(
            String eventType,
            String query,
            Boolean zeroResult,
            String matchedTargetId,
            String path,
            String referrer,
            String utmJson,
            String sessionId,
            String memberId,
            String geoCountry,
            String geoRegion,
            String metadataJson,
            Instant occurredAt) {
    }

    private static final String INSERT = """
            INSERT INTO analytics_event
                (id, tenant_id, event_type, query, zero_result, matched_target_id, path,
                 referrer, utm, session_id, member_id, geo_country, geo_region, metadata,
                 occurred_at, created_at, updated_at, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, CAST(? AS jsonb),
                    ?, NOW(), NOW(), ?, ?)
            """;

    /**
     * Batch-inserts a member/staff session's captured events under {@code tenantId}. No-op when
     * {@code events} is empty. {@code actor} stamps {@code created_by}/{@code updated_by}.
     */
    public void insertAll(String tenantId, String actor, List<AnalyticsEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, events, events.size(), (ps, e) -> {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, e.eventType());
            ps.setString(4, e.query());
            if (e.zeroResult() == null) {
                ps.setNull(5, Types.BOOLEAN);
            } else {
                ps.setBoolean(5, e.zeroResult());
            }
            ps.setString(6, e.matchedTargetId());
            ps.setString(7, e.path());
            ps.setString(8, e.referrer());
            ps.setString(9, e.utmJson() != null ? e.utmJson() : "{}");
            ps.setString(10, e.sessionId());
            ps.setString(11, e.memberId());
            ps.setString(12, e.geoCountry());
            ps.setString(13, e.geoRegion());
            ps.setString(14, e.metadataJson() != null ? e.metadataJson() : "{}");
            ps.setTimestamp(15, Timestamp.from(e.occurredAt() != null ? e.occurredAt() : Instant.now()));
            ps.setString(16, actor);
            ps.setString(17, actor);
        });
    }

    /** How many rows are older than {@code cutoff} (across all tenants — retention runs unscoped). */
    public long countOlderThan(Instant cutoff) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM analytics_event WHERE occurred_at < ?",
                Long.class, Timestamp.from(cutoff));
        return count == null ? 0L : count;
    }

    /**
     * Deletes up to {@code limit} rows older than {@code cutoff}, oldest first, claiming rows
     * with {@code FOR UPDATE SKIP LOCKED} so concurrent pods take disjoint slices. Returns the
     * number deleted.
     */
    public int deleteOlderThan(Instant cutoff, int limit) {
        return jdbc.update("""
                DELETE FROM analytics_event
                 WHERE id IN (
                       SELECT id FROM analytics_event
                        WHERE occurred_at < ?
                        ORDER BY occurred_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED)
                """, Timestamp.from(cutoff), limit);
    }
}
