package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sandbox provisioning + metadata promotion through the real stack (gateway →
 * worker → per-tenant Postgres → NATS). Exercises the V158 path Mockito tests
 * cannot reach: a real sandbox tenant is created via the tenants write path
 * (lifecycle hooks, RLS), the parent's metadata package is cloned in with a
 * natural-key remap, and a promotion imports a sandbox-side change back into
 * the parent tenant.
 *
 * <p>Flow: create two user collections with a LOOKUP between them → create a
 * SANDBOX environment → wait for the async clone (status ACTIVE) → prove via
 * DB that the sandbox tenant is parent-linked, the collections were cloned,
 * and the LOOKUP's {@code reference_collection_id} points at the SANDBOX
 * tenant's copy (the remap proof) → change a display name inside the sandbox
 * → create + approve + execute a FULL OVERWRITE promotion → the parent's
 * collection now carries the sandbox's change.
 */
@DisplayName("Sandbox Promotion Scenario")
class SandboxPromotionScenarioTest extends ScenarioBase {

    private static final String CUSTOMERS = "sbxcustomers";
    private static final String ORDERS = "sbxorders";
    private static final String PROMOTED_DISPLAY_NAME = "Promoted Customers";

    @Test
    @DisplayName("clones a sandbox tenant with remapped lookups and promotes a sandbox change to the parent")
    @SuppressWarnings("unchecked")
    void clonesSandboxAndPromotesChange() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        RestClient client = gatewayClientWithToken(token);
        String base = "/" + slug;

        waitForStatus(client, base + "/api/collections", HttpStatus.OK, 20);
        waitForStatus(client, base + "/api/environments", HttpStatus.OK, 20);

        // 1. Parent metadata worth cloning: two collections + a LOOKUP between them.
        String customersId = createCollection(client, base, CUSTOMERS, "Customers");
        String ordersId = createCollection(client, base, ORDERS, "Orders");
        addStringField(client, base, ordersId, "title");
        addLookupField(client, base, ordersId, "customer", customersId);

        // 2. Create the sandbox environment (async clone kicks off).
        Map<String, Object> envBody = Map.of("data", Map.of(
                "type", "environments",
                "attributes", Map.of("name", "dev", "type", "SANDBOX")));
        ResponseEntity<Map> created = client.post().uri(base + "/api/environments")
                .contentType(MediaType.APPLICATION_JSON).body(envBody)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map<String, Object> envData = (Map<String, Object>) created.getBody().get("data");
        String envId = (String) envData.get("id");
        assertThat(envId).isNotBlank();
        Map<String, Object> envAttrs = (Map<String, Object>) envData.get("attributes");
        assertThat(envAttrs.get("sandboxSlug")).isEqualTo(slug + "--dev");
        assertThat((String) envAttrs.get("adminInitialPassword"))
                .as("one-time sandbox admin credential is returned exactly once")
                .isNotBlank();

        // 3. Wait for the clone to finish (generous — full metadata export/import).
        waitForEnvironmentStatus(client, base, envId, 240);

        // 4. DB proof: sandbox tenant is parent-linked and the clone remapped by natural key.
        String sandboxTenantId;
        try (Connection db = openDbConnection()) {
            sandboxTenantId = queryString(db,
                    "SELECT id FROM tenant WHERE parent_tenant_id = ? AND slug = ?",
                    tenantId, slug + "--dev");
            assertThat(sandboxTenantId)
                    .as("sandbox tenant row exists with parent_tenant_id = parent")
                    .isNotNull();

            int clonedCount = queryInt(db,
                    "SELECT COUNT(*) FROM collection WHERE tenant_id = ? AND system_collection = false "
                            + "AND name IN (?, ?)",
                    sandboxTenantId, CUSTOMERS, ORDERS);
            assertThat(clonedCount).as("parent user collections were cloned").isEqualTo(2);

            // Remap proof: the LOOKUP in the sandbox points at the SANDBOX copy of
            // sbxcustomers, not the parent's row.
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT rc.tenant_id, rc.name FROM field f "
                            + "JOIN collection c ON f.collection_id = c.id "
                            + "JOIN collection rc ON f.reference_collection_id = rc.id "
                            + "WHERE c.tenant_id = ? AND c.name = ? AND f.name = ?")) {
                ps.setString(1, sandboxTenantId);
                ps.setString(2, ORDERS);
                ps.setString(3, "customer");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("cloned lookup field resolves to a collection").isTrue();
                    assertThat(rs.getString("tenant_id"))
                            .as("reference_collection_id was remapped into the sandbox tenant")
                            .isEqualTo(sandboxTenantId);
                    assertThat(rs.getString("name")).isEqualTo(CUSTOMERS);
                }
            }

            // 5. Make a sandbox-side metadata change to promote back. Done via direct
            // DB update: the sandbox admin's credential was rotated at provisioning and
            // the harness AuthFixture has no second-tenant login helper.
            try (PreparedStatement ps = db.prepareStatement(
                    "UPDATE collection SET display_name = ?, updated_at = NOW() "
                            + "WHERE tenant_id = ? AND name = ?")) {
                ps.setString(1, PROMOTED_DISPLAY_NAME);
                ps.setString(2, sandboxTenantId);
                ps.setString(3, CUSTOMERS);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }

        // 6. Create a FULL OVERWRITE promotion sandbox → production.
        String prodEnvId = findProductionEnvironmentId(client, base);
        Map<String, Object> promoBody = Map.of("data", Map.of(
                "type", "promotions",
                "attributes", Map.of(
                        "sourceEnvId", envId,
                        "targetEnvId", prodEnvId,
                        "promotionType", "FULL",
                        "conflictMode", "OVERWRITE")));
        ResponseEntity<Map> promoCreated = client.post().uri(base + "/api/promotions")
                .contentType(MediaType.APPLICATION_JSON).body(promoBody)
                .retrieve().toEntity(Map.class);
        assertThat(promoCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String promotionId = (String) ((Map<String, Object>) promoCreated.getBody().get("data")).get("id");

        // 7. Approve as a different user. Four-eyes enforcement (approver != creator,
        // PENDING-only) is unit-tested; the harness AuthFixture has no second-admin
        // login helper, so the approval row is written directly — execute still goes
        // through the API and its strict APPROVED check.
        try (Connection db = openDbConnection();
             PreparedStatement ps = db.prepareStatement(
                     "UPDATE environment_promotion SET status = 'APPROVED', approved_by = ?, "
                             + "approved_at = NOW(), updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, "second-admin-" + UUID.randomUUID());
            ps.setString(2, promotionId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        // 8. Execute (async) and wait for COMPLETED.
        ResponseEntity<Map> executed = client.post().uri(base + "/api/promotions/" + promotionId + "/execute")
                .retrieve().toEntity(Map.class);
        assertThat(executed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        waitForPromotionStatus(client, base, promotionId, 240);

        // 9. The sandbox-side change landed in the parent tenant.
        try (Connection db = openDbConnection()) {
            String displayName = queryString(db,
                    "SELECT display_name FROM collection WHERE tenant_id = ? AND name = ?",
                    tenantId, CUSTOMERS);
            assertThat(displayName)
                    .as("promotion applied the sandbox's display_name to the parent")
                    .isEqualTo(PROMOTED_DISPLAY_NAME);

            // The pre-promotion target snapshot exists — the rollback restore point.
            String snapshotId = queryString(db,
                    "SELECT target_snapshot_id FROM environment_promotion WHERE id = ?", promotionId);
            assertThat(snapshotId).as("execute snapshotted the target before importing").isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String createCollection(RestClient client, String base, String name, String displayName) {
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "collections",
                "attributes", Map.of(
                        "name", name,
                        "displayName", displayName,
                        "tenantScoped", true)));
        ResponseEntity<Map> response = client.post().uri(base + "/api/collections")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("collection '%s' create should succeed", name).isTrue();
        String id = (String) ((Map<String, Object>) response.getBody().get("data")).get("id");
        assertThat(id).isNotBlank();
        // Wait for the dynamic record route so subsequent work sees the collection live.
        waitForStatus(client, base + "/api/" + name, HttpStatus.OK, 30);
        return id;
    }

    @SuppressWarnings("unchecked")
    private void addStringField(RestClient client, String base, String collectionId, String fieldName) {
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "fields",
                "attributes", Map.of(
                        "collectionId", collectionId,
                        "name", fieldName,
                        "type", "STRING")));
        ResponseEntity<Map> response = client.post().uri(base + "/api/fields")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("field '%s' create should succeed", fieldName).isTrue();
    }

    /** Mirrors the MCP add_field lookup body: referenceCollectionId as a to-one relationship. */
    @SuppressWarnings("unchecked")
    private void addLookupField(RestClient client, String base, String collectionId,
                                String fieldName, String referenceCollectionId) {
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "fields",
                "attributes", Map.of(
                        "collectionId", collectionId,
                        "name", fieldName,
                        "type", "LOOKUP",
                        "relationshipName", fieldName),
                "relationships", Map.of(
                        "referenceCollectionId", Map.of(
                                "data", Map.of("type", "collections", "id", referenceCollectionId)))));
        ResponseEntity<Map> response = client.post().uri(base + "/api/fields")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("lookup field '%s' create should succeed", fieldName).isTrue();
    }

    @SuppressWarnings("unchecked")
    private String findProductionEnvironmentId(RestClient client, String base) {
        ResponseEntity<Map> response = client.get().uri(base + "/api/environments")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> environments = (List<Map<String, Object>>) response.getBody().get("data");
        return environments.stream()
                .filter(env -> {
                    Map<String, Object> attrs = (Map<String, Object>) env.get("attributes");
                    return attrs != null && "PRODUCTION".equals(attrs.get("type"));
                })
                .map(env -> (String) env.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No PRODUCTION environment row — sandbox creation should have ensured one"));
    }

    /** Polls the environment until ACTIVE; fails fast on FAILED. ~0.5s per attempt. */
    @SuppressWarnings("unchecked")
    private void waitForEnvironmentStatus(RestClient client, String base, String envId, int maxAttempts) {
        String lastStatus = "unknown";
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ResponseEntity<Map> response = client.get().uri(base + "/api/environments/" + envId)
                        .retrieve().toEntity(Map.class);
                Map<String, Object> attrs = (Map<String, Object>)
                        ((Map<String, Object>) response.getBody().get("data")).get("attributes");
                lastStatus = String.valueOf(attrs.get("status"));
                if ("ACTIVE".equals(lastStatus)) {
                    return;
                }
                if ("FAILED".equals(lastStatus)) {
                    throw new AssertionError("Sandbox clone FAILED for environment " + envId
                            + " — config=" + attrs.get("config"));
                }
            } catch (AssertionError e) {
                throw e;
            } catch (RuntimeException ignored) {
                // not ready yet
            }
            sleep();
        }
        throw new AssertionError("Environment " + envId + " did not become ACTIVE (last status: "
                + lastStatus + ")");
    }

    /** Polls the promotion until COMPLETED; fails fast on FAILED. ~0.5s per attempt. */
    @SuppressWarnings("unchecked")
    private void waitForPromotionStatus(RestClient client, String base, String promotionId, int maxAttempts) {
        String lastStatus = "unknown";
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ResponseEntity<Map> response = client.get().uri(base + "/api/promotions/" + promotionId)
                        .retrieve().toEntity(Map.class);
                Map<String, Object> attrs = (Map<String, Object>)
                        ((Map<String, Object>) response.getBody().get("data")).get("attributes");
                lastStatus = String.valueOf(attrs.get("status"));
                if ("COMPLETED".equals(lastStatus)) {
                    return;
                }
                if ("FAILED".equals(lastStatus)) {
                    throw new AssertionError("Promotion " + promotionId + " FAILED: "
                            + attrs.get("error_message"));
                }
            } catch (AssertionError e) {
                throw e;
            } catch (RuntimeException ignored) {
                // not ready yet
            }
            sleep();
        }
        throw new AssertionError("Promotion " + promotionId + " did not COMPLETE (last status: "
                + lastStatus + ")");
    }

    private String queryString(Connection db, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int queryInt(Connection db, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
