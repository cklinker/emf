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
 * The analytics-capture guarantees against real Postgres (consumer-alerting slice 8).
 *
 * <p>What only a real database can prove, and a mocked test cannot:
 * <ul>
 *   <li><b>RLS isolates tenants</b> — an {@code analytics_event} written under one tenant is
 *       invisible to another, verified as a non-superuser (the harness DB user bypasses RLS even
 *       on FORCE'd tables).</li>
 *   <li><b>The retention delete is valid SQL and prunes correctly</b> —
 *       {@code DELETE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED)} either works or fails at
 *       runtime, never at compile time; an old row goes, a fresh row stays, and a second pass is
 *       a no-op. The sweep runs unscoped (admin-bypass), which the superuser connection models.</li>
 * </ul>
 *
 * <p>The harness has no dependency on kelta-worker, so the retention statements below are copied
 * verbatim from {@code AnalyticsEventRepository} — <b>keep them in sync</b>, the same contract
 * {@code FlowLogRetentionScenarioTest} follows.
 */
@DisplayName("Analytics Capture Scenario")
class AnalyticsCaptureScenarioTest extends ScenarioBase {

    private static final String PROBE_ROLE = "kelta_harness_analytics_probe";
    private static final String PROBE_PASSWORD = "kelta-harness-analytics-probe";

    /** Verbatim from AnalyticsEventRepository.countOlderThan. */
    private static final String COUNT_OLDER_THAN =
            "SELECT COUNT(*) FROM analytics_event WHERE occurred_at < ?";

    /** Verbatim from AnalyticsEventRepository.deleteOlderThan. */
    private static final String DELETE_OLDER_THAN = """
            DELETE FROM analytics_event
             WHERE id IN (
                   SELECT id FROM analytics_event
                    WHERE occurred_at < ?
                    ORDER BY occurred_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
            """;

    @Test
    @DisplayName("RLS isolates one tenant's captured events from another's")
    void rlsIsolatesTenants() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenantA = "ac-a-" + suffix;
        String tenantB = "ac-b-" + suffix;
        Instant now = Instant.now();

        try (Connection db = openDbConnection()) {
            try {
                insertEvent(db, tenantA, "maroon bells campsite", now);
                insertEvent(db, tenantB, "global entry denver", now);

                ensureProbeRole(db);
                try (Connection probe = openDbConnection(PROBE_ROLE, PROBE_PASSWORD)) {
                    setTenant(probe, tenantA);
                    assertThat(eventTenants(probe))
                            .as("tenant A sees only its own analytics events")
                            .containsOnly(tenantA);

                    setTenant(probe, tenantB);
                    assertThat(eventTenants(probe))
                            .as("tenant B sees only its own analytics events")
                            .containsOnly(tenantB);
                }
            } finally {
                exec(db, "DELETE FROM analytics_event WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM analytics_event WHERE tenant_id = ?", tenantB);
            }
        }
    }

    @Test
    @DisplayName("retention prunes old events, spares fresh ones, and is idempotent")
    void retentionPrunesOldOnly() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenant = "ac-ret-" + suffix;
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(90));
        Instant old = now.minus(Duration.ofDays(120));

        try (Connection db = openDbConnection()) {
            String oldRow = null;
            String freshRow = null;
            try {
                oldRow = insertEvent(db, tenant, "old query", old);
                freshRow = insertEvent(db, tenant, "fresh query", now);

                assertThat(countOlderThan(db, cutoff))
                        .as("the old row is due").isGreaterThanOrEqualTo(1);

                int deleted = deleteOlderThan(db, cutoff, 1000);
                assertThat(deleted).as("delete statement executes and removes the old row")
                        .isGreaterThanOrEqualTo(1);

                assertThat(exists(db, oldRow)).as("old event purged").isFalse();
                assertThat(exists(db, freshRow)).as("fresh event retained").isTrue();

                assertThat(deleteOlderThan(db, cutoff, 1000))
                        .as("second run is a no-op").isZero();
            } finally {
                exec(db, "DELETE FROM analytics_event WHERE tenant_id = ?", tenant);
            }
        }
    }

    // ------------------------------------------------------------- Helpers

    private String insertEvent(Connection db, String tenantId, String query, Instant occurredAt)
            throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO analytics_event
                    (id, tenant_id, event_type, query, occurred_at, created_at, updated_at)
                VALUES (?, ?, 'SEARCH_QUERY', ?, ?, NOW(), NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, query);
            ps.setTimestamp(4, Timestamp.from(occurredAt));
            ps.executeUpdate();
        }
        return id;
    }

    private long countOlderThan(Connection db, Instant cutoff) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(COUNT_OLDER_THAN)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private int deleteOlderThan(Connection db, Instant cutoff, int limit) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(DELETE_OLDER_THAN)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            ps.setInt(2, limit);
            return ps.executeUpdate();
        }
    }

    private boolean exists(Connection db, String id) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT 1 FROM analytics_event WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> eventTenants(Connection probe) throws SQLException {
        List<String> tenants = new ArrayList<>();
        try (PreparedStatement ps = probe.prepareStatement("SELECT tenant_id FROM analytics_event");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tenants.add(rs.getString(1));
            }
        }
        return tenants;
    }

    /**
     * RLS needs a non-superuser: the harness DB user is the image's bootstrap (super)user and
     * bypasses RLS even on FORCE'd tables.
     */
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
            st.execute("GRANT SELECT ON \"" + schema + "\".analytics_event TO " + PROBE_ROLE);
        }
    }

    /** Session-scoped tenant binding — the same setting the worker's RLS relies on. */
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
