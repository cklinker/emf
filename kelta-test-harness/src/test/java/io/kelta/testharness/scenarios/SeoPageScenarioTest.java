package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEO page generation against real Postgres (consumer-alerting slice 11).
 *
 * <p>What only a real database can prove:
 * <ul>
 *   <li>The aggregate query joins watch_target ⨝ watch ⨝ win and counts <b>distinct active
 *       watchers</b> and <b>all wins</b> per target correctly.</li>
 *   <li>The upsert is idempotent on {@code (tenant_id, slug)} and the stale-prune removes an old
 *       page while sparing a fresh one.</li>
 *   <li>RLS isolates one tenant's pages from another's (non-superuser probe).</li>
 * </ul>
 *
 * <p>Queries are copied verbatim from {@code SeoPageRepository} — keep them in sync.
 */
@DisplayName("SEO Page Scenario")
class SeoPageScenarioTest extends ScenarioBase {

    private static final String PROBE_ROLE = "kelta_harness_seo_probe";
    private static final String PROBE_PASSWORD = "kelta-harness-seo-probe";

    /** Verbatim from SeoPageRepository.computeTargetAggregates. */
    private static final String COMPUTE = """
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
             WHERE wt.active = true AND wt.tenant_id = ?
            """;

    @Test
    @DisplayName("aggregate counts distinct active watchers and all wins; upsert + prune behave")
    void aggregateAndUpsert() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenant = "seo-" + suffix;
        Instant now = Instant.now();

        try (Connection db = openDbConnection()) {
            try {
                String target = insertTarget(db, tenant, "Maroon Bells " + suffix);
                // 3 distinct active watchers (+ one PAUSED that must NOT count) and 2 wins.
                insertWatch(db, tenant, "m1", target, "ACTIVE");
                insertWatch(db, tenant, "m2", target, "ACTIVE");
                insertWatch(db, tenant, "m3", target, "ACTIVE");
                insertWatch(db, tenant, "m4", target, "PAUSED");
                insertWin(db, tenant, target, now);
                insertWin(db, tenant, target, now.minus(Duration.ofDays(1)));

                int[] counts = aggregate(db, tenant, target);
                assertThat(counts[0]).as("distinct ACTIVE watchers").isEqualTo(3);
                assertThat(counts[1]).as("all wins").isEqualTo(2);

                // Upsert twice with the same slug → one row (idempotent).
                String slug = "maroon-bells-" + suffix;
                upsert(db, tenant, target, slug, "Maroon Bells", 3, 2, true, now);
                upsert(db, tenant, target, slug, "Maroon Bells", 3, 2, true, now);
                assertThat(countPages(db, tenant)).as("upsert is idempotent on (tenant, slug)").isEqualTo(1);

                // A stale page (older generated_at) is pruned; the fresh one survives.
                upsert(db, tenant, target, "stale-" + suffix, "Stale", 0, 0, false,
                        now.minus(Duration.ofDays(2)));
                int pruned = prune(db, now.minus(Duration.ofDays(1)));
                assertThat(pruned).isGreaterThanOrEqualTo(1);
                assertThat(pageExistsBySlug(db, tenant, slug)).as("fresh page kept").isTrue();
                assertThat(pageExistsBySlug(db, tenant, "stale-" + suffix)).as("stale page pruned").isFalse();
            } finally {
                exec(db, "DELETE FROM seo_page WHERE tenant_id = ?", tenant);
                exec(db, "DELETE FROM win WHERE tenant_id = ?", tenant);
                exec(db, "DELETE FROM watch WHERE tenant_id = ?", tenant);
                exec(db, "DELETE FROM watch_target WHERE tenant_id = ?", tenant);
            }
        }
    }

    @Test
    @DisplayName("RLS isolates one tenant's SEO pages from another's")
    void rlsIsolatesTenants() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenantA = "seo-a-" + suffix;
        String tenantB = "seo-b-" + suffix;
        Instant now = Instant.now();

        try (Connection db = openDbConnection()) {
            try {
                upsert(db, tenantA, null, "a-" + suffix, "A", 1, 1, true, now);
                upsert(db, tenantB, null, "b-" + suffix, "B", 1, 1, true, now);
                ensureProbeRole(db);
                try (Connection probe = openDbConnection(PROBE_ROLE, PROBE_PASSWORD)) {
                    setTenant(probe, tenantA);
                    assertThat(pageTenants(probe)).as("tenant A sees only its pages").containsOnly(tenantA);
                    setTenant(probe, tenantB);
                    assertThat(pageTenants(probe)).as("tenant B sees only its pages").containsOnly(tenantB);
                }
            } finally {
                exec(db, "DELETE FROM seo_page WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM seo_page WHERE tenant_id = ?", tenantB);
            }
        }
    }

    // ------------------------------------------------------------- Helpers

    private int[] aggregate(Connection db, String tenant, String target) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(COMPUTE)) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (target.equals(rs.getString("target_id"))) {
                        return new int[]{rs.getInt("watcher_count"), rs.getInt("win_count")};
                    }
                }
            }
        }
        return new int[]{-1, -1};
    }

    private void upsert(Connection db, String tenant, String targetId, String slug, String title,
                        int watchers, int wins, boolean published, Instant generatedAt)
            throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO seo_page (id, tenant_id, target_id, slug, title, watcher_count,
                                      win_count, published, generated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (tenant_id, slug) DO UPDATE SET
                    watcher_count = EXCLUDED.watcher_count, win_count = EXCLUDED.win_count,
                    published = EXCLUDED.published, generated_at = EXCLUDED.generated_at
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenant);
            ps.setString(3, targetId);
            ps.setString(4, slug);
            ps.setString(5, title);
            ps.setInt(6, watchers);
            ps.setInt(7, wins);
            ps.setBoolean(8, published);
            ps.setTimestamp(9, Timestamp.from(generatedAt));
            ps.executeUpdate();
        }
    }

    private int prune(Connection db, Instant cutoff) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "DELETE FROM seo_page WHERE generated_at < ?")) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            return ps.executeUpdate();
        }
    }

    private int countPages(Connection db, String tenant) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT COUNT(*) FROM seo_page WHERE tenant_id = ?")) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private boolean pageExistsBySlug(Connection db, String tenant, String slug) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT 1 FROM seo_page WHERE tenant_id = ? AND slug = ?")) {
            ps.setString(1, tenant);
            ps.setString(2, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> pageTenants(Connection probe) throws SQLException {
        List<String> tenants = new ArrayList<>();
        try (PreparedStatement ps = probe.prepareStatement("SELECT tenant_id FROM seo_page");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tenants.add(rs.getString(1));
            }
        }
        return tenants;
    }

    private String insertTarget(Connection db, String tenant, String name) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO watch_target (id, tenant_id, source, external_id, name, category,
                                          created_at, updated_at)
                VALUES (?, ?, 'recgov', ?, ?, 'campsites', NOW(), NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenant);
            ps.setString(3, "ext-" + id);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private void insertWatch(Connection db, String tenant, String member, String target, String status)
            throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO watch (id, tenant_id, member_id, target_id, criteria, channels, status,
                                   created_at, updated_at)
                VALUES (?, ?, ?, ?, '{}'::jsonb, '[]'::jsonb, ?, NOW(), NOW())
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenant);
            ps.setString(3, member);
            ps.setString(4, target);
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private void insertWin(Connection db, String tenant, String target, Instant claimedAt)
            throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO win (id, tenant_id, member_id, target_id, summary, claimed_at,
                                 created_at, updated_at)
                VALUES (?, ?, 'm1', ?, 'got it', ?, NOW(), NOW())
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenant);
            ps.setString(3, target);
            ps.setTimestamp(4, Timestamp.from(claimedAt));
            ps.executeUpdate();
        }
    }

    private void ensureProbeRole(Connection admin) throws SQLException {
        try (Statement st = admin.createStatement()) {
            st.execute("""
                    DO $$ BEGIN
                        CREATE ROLE %s LOGIN PASSWORD '%s';
                    EXCEPTION WHEN duplicate_object THEN NULL;
                    END $$
                    """.formatted(PROBE_ROLE, PROBE_PASSWORD));
            String schema;
            try (ResultSet rs = st.executeQuery("SELECT current_schema()")) {
                rs.next();
                schema = rs.getString(1);
            }
            st.execute("GRANT USAGE ON SCHEMA \"" + schema + "\" TO " + PROBE_ROLE);
            st.execute("GRANT SELECT ON \"" + schema + "\".seo_page TO " + PROBE_ROLE);
        }
    }

    private void setTenant(Connection conn, String tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId);
            ps.execute();
        }
    }

    private void exec(Connection db, String sql, String param) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        }
    }
}
