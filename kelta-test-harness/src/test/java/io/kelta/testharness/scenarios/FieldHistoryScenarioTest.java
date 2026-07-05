package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end read path for {@code field_history} against the real stack: gateway static route
 * → worker → the {@code field-history} read-only system collection → JSON:API. This is the wiring
 * most prone to silent breakage — a missing gateway route 404s, and a column/field mismatch between
 * the {@code field_history} table (V19/V35/V68) and the system-collection definition 500s. Neither
 * shows up in Mockito worker tests (the DB-constraint-test-gap lesson); the hook's diff/write logic
 * and the FLS/masking read guard are covered by worker unit tests. Harness Cerbos is dev allow-all,
 * so the security advice passes rows through unmasked here.
 *
 * <p>We seed one row directly (FK to a real seeded collection) rather than driving the write hook,
 * so the assertion is deterministic and independent of NATS config-refresh timing.
 */
@DisplayName("Field History Read Scenario")
class FieldHistoryScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("gateway routes /api/field-history and the system collection serves seeded rows")
    void fieldHistoryRowIsRoutableAndReadable() throws Exception {
        String token = auth.loginAsAdmin(TenantFixture.ECOMMERCE_SLUG);
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        String collectionId;
        String rowId = UUID.randomUUID().toString();
        String recordId = UUID.randomUUID().toString();

        try (Connection admin = openDbConnection()) {
            collectionId = firstCollectionId(admin, tenantId);
            assertThat(collectionId).as("ecommerce tenant has a seeded collection").isNotNull();

            try {
                insertHistoryRow(admin, rowId, tenantId, collectionId, recordId);

                // Route + system-collection read must return the seeded row through the gateway.
                waitForStatus(gatewayClientWithToken(token),
                        "/" + slug + "/api/field-history?filter[recordId][eq]=" + recordId,
                        HttpStatus.OK, 20);

                @SuppressWarnings("unchecked")
                Map<String, Object> body = gatewayClientWithToken(token)
                        .get()
                        .uri("/" + slug + "/api/field-history?filter[recordId][eq]=" + recordId)
                        .retrieve()
                        .body(Map.class);

                assertThat(body).containsKey("data");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                assertThat(data).as("seeded field-history row is returned").hasSize(1);

                @SuppressWarnings("unchecked")
                Map<String, Object> attrs = (Map<String, Object>) data.get(0).get("attributes");
                assertThat(attrs.get("fieldName")).isEqualTo("status");
                assertThat(attrs.get("recordId")).isEqualTo(recordId);
                assertThat(attrs.get("changeSource")).isEqualTo("UI");
                // old/new JSONB round-trips as parsed JSON values, not raw text.
                assertThat(attrs.get("oldValue")).isEqualTo("NEW");
                assertThat(attrs.get("newValue")).isEqualTo("DONE");
            } finally {
                deleteHistoryRow(admin, rowId);
            }
        }
    }

    private String firstCollectionId(Connection conn, String tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM collection WHERE tenant_id = ? ORDER BY id LIMIT 1")) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id") : null;
            }
        }
    }

    private void insertHistoryRow(Connection conn, String id, String tenantId,
                                  String collectionId, String recordId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO field_history
                    (id, tenant_id, collection_id, record_id, field_name,
                     old_value, new_value, changed_by, change_source)
                VALUES (?, ?, ?, ?, 'status', CAST(? AS jsonb), CAST(? AS jsonb), 'user-1', 'UI')
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, collectionId);
            ps.setString(4, recordId);
            ps.setString(5, "\"NEW\"");
            ps.setString(6, "\"DONE\"");
            ps.executeUpdate();
        }
    }

    private void deleteHistoryRow(Connection conn, String id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM field_history WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }
}
