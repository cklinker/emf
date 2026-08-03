package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The watch model's guarantees against real Postgres (consumer-alerting slice 3).
 *
 * <p>Everything asserted here is enforced by the database, not by application
 * logic, which is exactly why a mocked repository test proves none of it:
 * <ul>
 *   <li><b>Target identity</b> — {@code (tenant, source, external_id)} is unique,
 *       so a poller's report resolves to at most one target; two sources reusing
 *       an upstream id do not collide.</li>
 *   <li><b>Alert dedupe</b> — {@code (tenant, watch, slot, episode)} is unique.
 *       This is the anti-spam guarantee: one alert per member per slot per
 *       opening, and a genuine reopen (new episode) alerts again.</li>
 *   <li><b>Targets with live watches cannot be deleted</b> — the FK is NO ACTION
 *       on purpose, so removing a target fails loudly instead of silently
 *       discarding members' watches.</li>
 *   <li><b>Deliveries follow their alert</b> via ON DELETE CASCADE.</li>
 *   <li><b>RLS isolates tenants</b> — verified as a non-superuser, since the
 *       harness DB user bypasses RLS even on FORCE'd tables.</li>
 * </ul>
 */
@DisplayName("Watch Model Scenario")
class WatchModelScenarioTest extends ScenarioBase {

    private static final String PROBE_ROLE = "kelta_harness_watch_probe";
    private static final String PROBE_PASSWORD = "kelta-harness-watch-probe";

    @Test
    @DisplayName("database enforces target identity, alert dedupe, and referential rules")
    void databaseEnforcesWatchModelInvariants() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenantA = "wm-a-" + suffix;
        String tenantB = "wm-b-" + suffix;

        try (Connection db = openDbConnection()) {
            try {
                String targetA = insertTarget(db, tenantA, "recgov", "site-" + suffix, "Site A");

                // ---- (tenant, source, external_id) is unique
                assertThatThrownBy(() ->
                        insertTarget(db, tenantA, "recgov", "site-" + suffix, "Duplicate"))
                        .as("same source + external id in one tenant is rejected")
                        .isInstanceOf(SQLException.class);

                // ---- the same upstream id under a different source is a different thing
                String targetOtherSource =
                        insertTarget(db, tenantA, "ttp", "site-" + suffix, "Other source");
                assertThat(targetOtherSource).isNotEqualTo(targetA);

                // ---- and a different tenant may reuse it entirely
                String targetB = insertTarget(db, tenantB, "recgov", "site-" + suffix, "Tenant B");
                assertThat(targetB).isNotEqualTo(targetA);

                String watchA = insertWatch(db, tenantA, "member-1", targetA);

                // ---- a target with live watches cannot be deleted
                assertThatThrownBy(() -> exec(db, "DELETE FROM watch_target WHERE id = ?", targetA))
                        .as("FK is NO ACTION so members' watches are never silently discarded")
                        .isInstanceOf(SQLException.class);

                // ---- one alert per (watch, slot, episode)
                String slot = "2026-08-14";
                String alert1 = insertAlert(db, tenantA, watchA, targetA, slot, "episode-1");
                assertThatThrownBy(() ->
                        insertAlert(db, tenantA, watchA, targetA, slot, "episode-1"))
                        .as("re-alerting the same opening is rejected by the dedupe key")
                        .isInstanceOf(SQLException.class);

                // ---- a genuine reopen is a new episode and alerts again
                String alert2 = insertAlert(db, tenantA, watchA, targetA, slot, "episode-2");
                assertThat(alert2).isNotEqualTo(alert1);

                // ---- deliveries follow their alert
                insertDelivery(db, alert1, "push");
                insertDelivery(db, alert1, "email");
                assertThat(countDeliveries(db, alert1)).isEqualTo(2);
                exec(db, "DELETE FROM alert WHERE id = ?", alert1);
                assertThat(countDeliveries(db, alert1))
                        .as("deliveries cascade with their alert").isZero();

                // ---- RLS isolates tenants (checked as a non-superuser)
                insertWatch(db, tenantB, "member-2", targetB);
                ensureProbeRole(db);
                try (Connection probe = openDbConnection(PROBE_ROLE, PROBE_PASSWORD)) {
                    setTenant(probe, tenantA);
                    assertThat(watchTenants(probe))
                            .as("tenant A sees only its own watches")
                            .containsOnly(tenantA);

                    setTenant(probe, tenantB);
                    assertThat(watchTenants(probe))
                            .as("tenant B sees only its own watches")
                            .containsOnly(tenantB);
                }
            } finally {
                // alert_delivery cascades; watches must go before their targets.
                exec(db, "DELETE FROM alert WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM alert WHERE tenant_id = ?", tenantB);
                exec(db, "DELETE FROM watch WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM watch WHERE tenant_id = ?", tenantB);
                exec(db, "DELETE FROM availability_state WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM watch_target WHERE tenant_id = ?", tenantA);
                exec(db, "DELETE FROM watch_target WHERE tenant_id = ?", tenantB);
            }
        }
    }

    @Test
    @DisplayName("an episode alerts once however many times the slot is re-observed")
    void episodeDedupeSurvivesRepeatObservations() throws Exception {
        String suffix = Long.toHexString(System.nanoTime());
        String tenant = "wm-ep-" + suffix;

        try (Connection db = openDbConnection()) {
            try {
                String target = insertTarget(db, tenant, "recgov", "ep-" + suffix, "Episode Site");
                String watch = insertWatch(db, tenant, "member-1", target);
                String slot = "2026-09-01";

                // The matcher would attempt a claim on every poll; only the first
                // of an episode may succeed.
                int accepted = 0;
                for (int i = 0; i < 5; i++) {
                    if (tryInsertAlert(db, tenant, watch, target, slot, "episode-A")) {
                        accepted++;
                    }
                }
                assertThat(accepted).as("five polls of one opening yield one alert").isEqualTo(1);

                assertThat(tryInsertAlert(db, tenant, watch, target, slot, "episode-B"))
                        .as("a genuine reopen alerts again").isTrue();
            } finally {
                exec(db, "DELETE FROM alert WHERE tenant_id = ?", tenant);
                exec(db, "DELETE FROM watch WHERE tenant_id = ?", tenant);
                exec(db, "DELETE FROM watch_target WHERE tenant_id = ?", tenant);
            }
        }
    }

    // ------------------------------------------------------------- Helpers

    private String insertTarget(Connection db, String tenantId, String source,
                                String externalId, String name) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO watch_target (id, tenant_id, source, external_id, name,
                                          created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, source);
            ps.setString(4, externalId);
            ps.setString(5, name);
            ps.executeUpdate();
        }
        return id;
    }

    private String insertWatch(Connection db, String tenantId, String memberId, String targetId)
            throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO watch (id, tenant_id, member_id, target_id, criteria, channels,
                                   status, created_at, updated_at)
                VALUES (?, ?, ?, ?, '{"v":1}'::jsonb, '["push"]'::jsonb, 'ACTIVE', NOW(), NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, memberId);
            ps.setString(4, targetId);
            ps.executeUpdate();
        }
        return id;
    }

    private String insertAlert(Connection db, String tenantId, String watchId, String targetId,
                               String slotKey, String episodeId) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO alert (id, tenant_id, watch_id, target_id, slot_key, episode_id,
                                   created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, watchId);
            ps.setString(4, targetId);
            ps.setString(5, slotKey);
            ps.setString(6, episodeId);
            ps.executeUpdate();
        }
        return id;
    }

    /** Mirrors {@code AlertRepository.claim}: the insert IS the dedupe claim. */
    private boolean tryInsertAlert(Connection db, String tenantId, String watchId, String targetId,
                                   String slotKey, String episodeId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO alert (id, tenant_id, watch_id, target_id, slot_key, episode_id,
                                   created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, watch_id, slot_key, episode_id) DO NOTHING
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, watchId);
            ps.setString(4, targetId);
            ps.setString(5, slotKey);
            ps.setString(6, episodeId);
            return ps.executeUpdate() > 0;
        }
    }

    private void insertDelivery(Connection db, String alertId, String channel) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO alert_delivery (id, alert_id, channel, status, created_at)
                VALUES (?, ?, ?, 'PENDING', NOW())
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, alertId);
            ps.setString(3, channel);
            ps.executeUpdate();
        }
    }

    private int countDeliveries(Connection db, String alertId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT COUNT(*) FROM alert_delivery WHERE alert_id = ?")) {
            ps.setString(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private java.util.List<String> watchTenants(Connection probe) throws SQLException {
        java.util.List<String> tenants = new java.util.ArrayList<>();
        try (PreparedStatement ps = probe.prepareStatement("SELECT tenant_id FROM watch");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tenants.add(rs.getString(1));
            }
        }
        return tenants;
    }

    /**
     * RLS needs a non-superuser: the harness DB user is the image's bootstrap
     * (super)user and bypasses RLS even on FORCE'd tables.
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
            st.execute("GRANT SELECT ON \"" + schema + "\".watch, \""
                    + schema + "\".watch_target TO " + PROBE_ROLE);
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
