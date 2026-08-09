package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Reads target aggregates and writes {@code seo_page} rows for the SEO generation sweep
 * (consumer-alerting slice 11).
 *
 * <p>The generation sweep runs on the scheduler thread with no tenant bound, so the connection
 * carries {@code app.current_tenant_id = ''} → the {@code admin_bypass} policy lets it read every
 * tenant's targets/watches/wins and upsert each tenant's pages in one pass — the same mechanism
 * {@link FlowLogRetentionRepository} / {@link AnalyticsEventRepository} rely on. Hand-written SQL
 * on {@link JdbcTemplate} — no JPA.
 */
@Repository
public class SeoPageRepository {

    private final JdbcTemplate jdbc;

    public SeoPageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Aggregate demand/success signals for one active target, across all tenants. */
    public record TargetAggregate(
            String tenantId,
            String targetId,
            String name,
            String category,
            int watcherCount,
            int winCount,
            Instant lastWinAt) {
    }

    /** A fully-resolved page row ready to upsert. {@code statsJson} is a pre-serialized string. */
    public record SeoPageRow(
            String id,
            String tenantId,
            String targetId,
            String slug,
            String title,
            String category,
            int watcherCount,
            int winCount,
            Instant lastWinAt,
            String statsJson,
            boolean published,
            Instant generatedAt) {
    }

    /**
     * One row per active target with its aggregate watcher/win counts. Active-watch watchers are
     * counted distinct by member; wins are all-time. Correlated via LEFT JOINs so a target with no
     * watchers/wins still yields a row (count 0) — the guardrail then decides publishability.
     */
    public List<TargetAggregate> computeTargetAggregates() {
        return jdbc.query("""
                SELECT wt.tenant_id, wt.id AS target_id, wt.name, wt.category,
                       COALESCE(w.watcher_count, 0)  AS watcher_count,
                       COALESCE(wn.win_count, 0)     AS win_count,
                       wn.last_win_at
                  FROM watch_target wt
                  LEFT JOIN (SELECT tenant_id, target_id,
                                    COUNT(DISTINCT member_id) AS watcher_count
                               FROM watch WHERE status = 'ACTIVE'
                              GROUP BY tenant_id, target_id) w
                    ON w.tenant_id = wt.tenant_id AND w.target_id = wt.id
                  LEFT JOIN (SELECT tenant_id, target_id,
                                    COUNT(*) AS win_count, MAX(claimed_at) AS last_win_at
                               FROM win
                              GROUP BY tenant_id, target_id) wn
                    ON wn.tenant_id = wt.tenant_id AND wn.target_id = wt.id
                 WHERE wt.active = true
                """, (rs, i) -> new TargetAggregate(
                rs.getString("tenant_id"),
                rs.getString("target_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getInt("watcher_count"),
                rs.getInt("win_count"),
                toInstant(rs.getTimestamp("last_win_at"))));
    }

    /** Inserts or updates the page keyed by {@code (tenant_id, slug)}. */
    public void upsert(SeoPageRow row) {
        jdbc.update("""
                INSERT INTO seo_page
                    (id, tenant_id, target_id, slug, title, category, watcher_count, win_count,
                     last_win_at, stats, published, generated_at, created_at, updated_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, NOW(), NOW(), 'seo-generator')
                ON CONFLICT (tenant_id, slug) DO UPDATE SET
                    target_id     = EXCLUDED.target_id,
                    title         = EXCLUDED.title,
                    category      = EXCLUDED.category,
                    watcher_count = EXCLUDED.watcher_count,
                    win_count     = EXCLUDED.win_count,
                    last_win_at   = EXCLUDED.last_win_at,
                    stats         = EXCLUDED.stats,
                    published     = EXCLUDED.published,
                    generated_at  = EXCLUDED.generated_at,
                    updated_at    = NOW()
                """,
                row.id(), row.tenantId(), row.targetId(), row.slug(), row.title(), row.category(),
                row.watcherCount(), row.winCount(),
                row.lastWinAt() == null ? null : Timestamp.from(row.lastWinAt()),
                row.statsJson() != null ? row.statsJson() : "{}",
                row.published(), Timestamp.from(row.generatedAt()));
    }

    /**
     * Prunes pages not regenerated in the current cycle (their target went inactive/away, or its
     * name changed and produced a new slug). Runs unscoped across tenants like the upsert.
     */
    public int deleteGeneratedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM seo_page WHERE generated_at < ?", Timestamp.from(cutoff));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
