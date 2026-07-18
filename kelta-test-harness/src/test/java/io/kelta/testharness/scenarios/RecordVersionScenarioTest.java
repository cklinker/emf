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
 * End-to-end read path for {@code record_version} against the real stack: gateway static route
 * → worker → the {@code record-versions} read-only system collection → JSON:API. Mirrors
 * {@link FieldHistoryScenarioTest} — a missing gateway route 404s and a column/field mismatch
 * between the {@code record_version} table (V174) and the system-collection definition 500s;
 * neither shows up in Mockito worker tests. Also exercises the atomic
 * {@code INSERT ... SELECT MAX(version_number)+1} numbering used by the repository: two
 * sequential inserts for the same record must land as versions 1 and 2 under the
 * {@code uq_record_version} unique constraint.
 *
 * <p>Rows are seeded directly (FK to a real seeded collection) so the assertion is
 * deterministic and independent of NATS config-refresh timing. Harness Cerbos is dev
 * allow-all, so the {@code RecordVersionSecurityAdvice} passes snapshots through unfiltered.
 */
@DisplayName("Record Version Read Scenario")
class RecordVersionScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("gateway routes /api/record-versions and sequential versions number 1,2")
    void recordVersionRowsAreRoutableAndNumbered() throws Exception {
        String token = auth.loginAsAdmin(TenantFixture.ECOMMERCE_SLUG);
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        String recordId = UUID.randomUUID().toString();

        try (Connection admin = openDbConnection()) {
            String collectionId = firstCollectionId(admin, tenantId);
            assertThat(collectionId).as("ecommerce tenant has a seeded collection").isNotNull();

            try {
                // Two sequential inserts — the INSERT..SELECT MAX+1 numbering must yield 1 then 2.
                insertVersionRow(admin, tenantId, collectionId, recordId,
                        "CREATED", "{\"status\": \"NEW\"}", "[\"status\"]");
                insertVersionRow(admin, tenantId, collectionId, recordId,
                        "UPDATED", "{\"status\": \"DONE\"}", "[\"status\"]");

                waitForStatus(gatewayClientWithToken(token),
                        "/" + slug + "/api/record-versions?filter[recordId][eq]=" + recordId,
                        HttpStatus.OK, 20);

                @SuppressWarnings("unchecked")
                Map<String, Object> body = gatewayClientWithToken(token)
                        .get()
                        .uri("/" + slug + "/api/record-versions?filter[recordId][eq]=" + recordId
                                + "&sort=versionNumber")
                        .retrieve()
                        .body(Map.class);

                assertThat(body).containsKey("data");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                assertThat(data).as("both seeded record-version rows are returned").hasSize(2);

                @SuppressWarnings("unchecked")
                Map<String, Object> first = (Map<String, Object>) data.get(0).get("attributes");
                @SuppressWarnings("unchecked")
                Map<String, Object> second = (Map<String, Object>) data.get(1).get("attributes");
                assertThat(List.of(numberOf(first), numberOf(second)))
                        .containsExactlyInAnyOrder(1, 2);
                Map<String, Object> v1 = numberOf(first) == 1 ? first : second;
                Map<String, Object> v2 = numberOf(first) == 2 ? first : second;
                assertThat(v1.get("changeType")).isEqualTo("CREATED");
                assertThat(v2.get("changeType")).isEqualTo("UPDATED");
                // JSONB round-trips as parsed JSON values, not raw text.
                assertThat(v2.get("snapshot")).isEqualTo(Map.of("status", "DONE"));
                assertThat(v2.get("changedFields")).isEqualTo(List.of("status"));
                assertThat(v2.get("changedBy")).isEqualTo("user-1");
            } finally {
                deleteVersionRows(admin, recordId);
            }
        }
    }

    private static int numberOf(Map<String, Object> attrs) {
        return ((Number) attrs.get("versionNumber")).intValue();
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

    /** Same atomic numbering statement the worker repository uses. */
    private void insertVersionRow(Connection conn, String tenantId, String collectionId,
                                  String recordId, String changeType, String snapshotJson,
                                  String changedFieldsJson) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO record_version
                    (id, tenant_id, collection_id, record_id, version_number, change_type,
                     snapshot, changed_fields, changed_by, change_source)
                SELECT ?, ?, ?, ?, COALESCE(MAX(version_number), 0) + 1, ?,
                       CAST(? AS jsonb), CAST(? AS jsonb), 'user-1', 'UI'
                FROM record_version
                WHERE tenant_id = ? AND collection_id = ? AND record_id = ?
                """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, collectionId);
            ps.setString(4, recordId);
            ps.setString(5, changeType);
            ps.setString(6, snapshotJson);
            ps.setString(7, changedFieldsJson);
            ps.setString(8, tenantId);
            ps.setString(9, collectionId);
            ps.setString(10, recordId);
            ps.executeUpdate();
        }
    }

    private void deleteVersionRows(Connection conn, String recordId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM record_version WHERE record_id = ?")) {
            ps.setString(1, recordId);
            ps.executeUpdate();
        }
    }
}
