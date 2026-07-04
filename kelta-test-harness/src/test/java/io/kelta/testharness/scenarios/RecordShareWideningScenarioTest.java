package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Record-share <em>widening lookup</em> semantics against real RLS-enabled Postgres —
 * the query behind {@code RecordShareRepository.findSharesForPrincipal} (kelta-worker):
 * join {@code record_share → collection} by collection <em>name</em>, USER-direct +
 * GROUP-membership grants via IN-lists, scoped to the current tenant by the V150
 * {@code tenant_isolation} RLS policy.
 *
 * <p>The harness Cerbos policies are dev allow-all, so the widen path
 * ({@code CerbosRecordAuthorizationAdvice} → {@code RecordShareAccessService}) never
 * fires end-to-end here — no record is ever denied. This scenario instead runs the
 * repository's exact SQL over real share rows, which is precisely what Mockito worker
 * tests can't verify (see the DB-constraint-test-gap lesson): the join column names,
 * the IN-list matching, and the RLS policy itself.
 *
 * <p><b>RLS needs a non-superuser:</b> the harness DB user is the image's bootstrap
 * (super)user and bypasses RLS even on FORCE'd tables, so the SELECTs run as a dedicated
 * probe role created by the test. The SQL below mirrors
 * {@code RecordShareRepository.findSharesForPrincipal} — keep them in sync.
 */
@DisplayName("Record Share Widening Scenario")
class RecordShareWideningScenarioTest extends ScenarioBase {

    private static final String PROBE_ROLE     = "kelta_harness_rls_probe";
    private static final String PROBE_PASSWORD = "kelta-harness-rls-probe";

    /** Mirror of RecordShareRepository.findSharesForPrincipal with 5 record + 2 group slots. */
    private static final String WIDENING_SQL = """
            SELECT rs.record_id, rs.access_level
            FROM record_share rs
            JOIN collection c ON c.id = rs.collection_id
            WHERE c.name = ?
              AND rs.record_id IN (?,?,?,?,?)
              AND (
                (rs.shared_with_type = 'USER' AND rs.shared_with_id = ?)
                OR (rs.shared_with_type = 'GROUP' AND rs.shared_with_id IN (?,?))
              )
            """;

    /** The repository's groupIds-empty branch emits no GROUP clause at all. */
    private static final String WIDENING_SQL_USER_ONLY = """
            SELECT rs.record_id, rs.access_level
            FROM record_share rs
            JOIN collection c ON c.id = rs.collection_id
            WHERE c.name = ?
              AND rs.record_id IN (?,?,?,?,?)
              AND (
                (rs.shared_with_type = 'USER' AND rs.shared_with_id = ?)
              )
            """;

    @Test
    @DisplayName("returns USER + GROUP grants for the principal, excludes other principals, and RLS scopes rows by tenant")
    void widensByUserAndGroupUnderRls() throws Exception {
        // Tenant A: the seeded ecommerce tenant (it has real collections to join against).
        String token = auth.loginAsAdmin(TenantFixture.ECOMMERCE_SLUG);
        String tenantA = auth.extractTenantId(token);
        // Tenant B: the seeded default tenant — used to prove cross-tenant invisibility.
        String tenantB = tenants.tenantIdForSlug(TenantFixture.DEFAULT_SLUG);
        assertThat(tenantB).isNotBlank().isNotEqualTo(tenantA);

        // A real collection row (id + name) so the join-by-name has something to hit. It must
        // be a collection row *owned by tenant A* — system collections belong to the platform
        // sentinel tenant, and the V77 tenant_isolation policy on `collection` would hide them
        // from the tenant-scoped probe, silently emptying the join.
        String collectionId;
        String collectionName;
        try (Connection admin = openDbConnection();
             PreparedStatement ps = admin.prepareStatement(
                     "SELECT id, name FROM collection WHERE tenant_id = ? LIMIT 1")) {
            ps.setString(1, tenantA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("ecommerce tenant has a seeded collection").isTrue();
                collectionId = rs.getString("id");
                collectionName = rs.getString("name");
            }
        }
        assertThat(collectionId).isNotBlank();
        assertThat(collectionName).isNotBlank();

        String suffix = Long.toHexString(System.nanoTime());
        String recUser       = "wid-" + suffix + "-user-rec";
        String recGroup      = "wid-" + suffix + "-group-rec";
        String recOtherUser  = "wid-" + suffix + "-other-user-rec";
        String recOtherGroup = "wid-" + suffix + "-other-group-rec";
        String recAbsent     = "wid-" + suffix + "-absent-rec";

        String userId     = "wid-user-" + suffix;
        String groupOne   = "wid-grp1-" + suffix;
        String groupTwo   = "wid-grp2-" + suffix;
        String otherUser  = "wid-other-user-" + suffix;
        String otherGroup = "wid-other-grp-" + suffix;

        try (Connection admin = openDbConnection()) {
            ensureProbeRole(admin);

            // Seed share rows with explicit tenant ids (the admin connection bypasses RLS,
            // exactly like Flyway seeds do). Row 5 is the RLS canary: same record + same
            // user as row 1 but in tenant B — if the policy leaked, the tenant-A query
            // would return recUser twice with two access levels.
            insertShare(admin, tenantA, collectionId, recUser,       userId,     "USER",  "READ");
            insertShare(admin, tenantA, collectionId, recGroup,      groupOne,   "GROUP", "EDIT");
            insertShare(admin, tenantA, collectionId, recOtherUser,  otherUser,  "USER",  "READ");
            insertShare(admin, tenantA, collectionId, recOtherGroup, otherGroup, "GROUP", "EDIT");
            insertShare(admin, tenantB, collectionId, recUser,       userId,     "USER",  "EDIT");

            try (Connection probe = openDbConnection(PROBE_ROLE, PROBE_PASSWORD)) {
                // ── Tenant A ────────────────────────────────────────────────────────
                setTenant(probe, tenantA);

                // USER-direct + GROUP grants, decoy principals excluded, absent record ignored.
                Map<String, String> grants = queryWidening(probe, WIDENING_SQL, collectionName,
                        List.of(recUser, recGroup, recOtherUser, recOtherGroup, recAbsent),
                        userId, List.of(groupTwo, groupOne));
                assertThat(grants)
                        .as("user-direct + group grants, nothing from other principals or tenants")
                        .containsOnly(
                                Map.entry(recUser, "READ"),
                                Map.entry(recGroup, "EDIT"));

                // Empty-groups branch: no GROUP clause — the group-shared record drops out.
                Map<String, String> userOnly = queryWidening(probe, WIDENING_SQL_USER_ONLY, collectionName,
                        List.of(recUser, recGroup, recOtherUser, recOtherGroup, recAbsent),
                        userId, List.of());
                assertThat(userOnly)
                        .as("without group ids only the user-direct share matches")
                        .containsOnly(Map.entry(recUser, "READ"));

                // Join is by collection *name* — a wrong name must match nothing even
                // though the share rows reference a valid collection id.
                Map<String, String> wrongName = queryWidening(probe, WIDENING_SQL,
                        "no_such_collection_" + suffix,
                        List.of(recUser, recGroup, recOtherUser, recOtherGroup, recAbsent),
                        userId, List.of(groupTwo, groupOne));
                assertThat(wrongName).as("join by collection name misses on a wrong name").isEmpty();

                // ── Tenant B ────────────────────────────────────────────────────────
                setTenant(probe, tenantB);

                // RLS row visibility: tenant B sees only its own share on recUser, with
                // its own access level — none of tenant A's four rows.
                try (PreparedStatement ps = probe.prepareStatement(
                        "SELECT record_id, access_level FROM record_share WHERE record_id LIKE ?")) {
                    ps.setString(1, "wid-" + suffix + "-%");
                    Map<String, String> visible = new HashMap<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            visible.put(rs.getString("record_id"), rs.getString("access_level"));
                        }
                    }
                    assertThat(visible)
                            .as("RLS: tenant B sees only its own share row")
                            .containsOnly(Map.entry(recUser, "EDIT"));
                }
            }
        } finally {
            try (Connection admin = openDbConnection();
                 PreparedStatement ps = admin.prepareStatement(
                         "DELETE FROM record_share WHERE record_id LIKE ?")) {
                ps.setString(1, "wid-" + suffix + "-%");
                ps.executeUpdate();
            }
        }
    }

    /**
     * Creates the non-superuser probe role (idempotent — roles are cluster-wide and the
     * CI DB pool is shared across runs) and grants it read access to the two tables in
     * the harness schema. {@code current_schema()} honours the CI URL's
     * {@code currentSchema=ci_<run-tag>} pin, so grants land on this run's schema.
     */
    private void ensureProbeRole(Connection admin) throws Exception {
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
            st.execute("GRANT SELECT ON \"" + schema + "\".record_share, \""
                    + schema + "\".collection TO " + PROBE_ROLE);
        }
    }

    private void insertShare(Connection conn, String tenantId, String collectionId,
                             String recordId, String sharedWithId, String sharedWithType,
                             String accessLevel) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO record_share
                    (id, tenant_id, collection_id, record_id, shared_with_id, shared_with_type, access_level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, collectionId);
            ps.setString(4, recordId);
            ps.setString(5, sharedWithId);
            ps.setString(6, sharedWithType);
            ps.setString(7, accessLevel);
            ps.executeUpdate();
        }
    }

    /** Session-scoped tenant binding — same setting the worker's RLS relies on. */
    private void setTenant(Connection conn, String tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId);
            ps.execute();
        }
    }

    /** Runs a widening query and returns {@code recordId → accessLevel}. */
    private Map<String, String> queryWidening(Connection conn, String sql, String collectionName,
                                              List<String> recordIds, String userId,
                                              List<String> groupIds) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, collectionName);
            for (String recordId : recordIds) {
                ps.setString(i++, recordId);
            }
            ps.setString(i++, userId);
            for (String groupId : groupIds) {
                ps.setString(i++, groupId);
            }
            Map<String, String> result = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("record_id"), rs.getString("access_level"));
                }
            }
            return result;
        }
    }
}
