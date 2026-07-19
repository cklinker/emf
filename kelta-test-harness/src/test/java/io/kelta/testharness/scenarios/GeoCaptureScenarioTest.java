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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * captureGeo end-to-end against the real stack: flip the flag on a live collection
 * (collections PATCH → NATS config event → per-pod refresh → idempotent
 * {@code ADD COLUMN IF NOT EXISTS} on real Postgres), then write a record with the
 * gateway-trusted {@code X-Geo-*} headers and assert the JSONB stamp persists and
 * round-trips. This is the load-bearing test for the JSONB half — H2 unit tests cannot
 * exercise Postgres JSONB DDL or storage (the DB-constraint-test-gap lesson).
 *
 * <p>The record is written via the worker directly (harness plays the gateway's role and
 * supplies the trusted headers — real clients can never do this because the gateway's
 * IdentityHeaderStripFilter strips client-supplied X-Geo-*).
 */
@DisplayName("Geo Capture Scenario")
class GeoCaptureScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("captureGeo flip adds the geo columns and record writes stamp JSONB geo")
    @SuppressWarnings("unchecked")
    void captureGeoFlipAndStampRoundTrips() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        RestClient client = gatewayClientWithToken(token);

        String collectionName = "geocaptest";

        waitForStatus(client, "/" + slug + "/api/collections", HttpStatus.OK, 20);

        // 1. Create a user collection (captureGeo defaults off) with one field.
        Map<String, Object> collectionBody = Map.of("data", Map.of(
                "type", "collections",
                "attributes", Map.of(
                        "name", collectionName,
                        "displayName", "Geo Capture Test",
                        "tenantScoped", true)));
        ResponseEntity<Map> createdCollection = client.post().uri("/" + slug + "/api/collections")
                .contentType(MediaType.APPLICATION_JSON).body(collectionBody)
                .retrieve().toEntity(Map.class);
        assertThat(createdCollection.getStatusCode().is2xxSuccessful()).isTrue();
        String collectionId = (String) ((Map<String, Object>) createdCollection.getBody().get("data")).get("id");

        waitForStatus(client, "/" + slug + "/api/" + collectionName, HttpStatus.OK, 30);

        Map<String, Object> fieldBody = Map.of("data", Map.of(
                "type", "fields",
                "attributes", Map.of(
                        "collectionId", collectionId,
                        "name", "title",
                        "type", "STRING")));
        ResponseEntity<Map> createdField = client.post().uri("/" + slug + "/api/fields")
                .contentType(MediaType.APPLICATION_JSON).body(fieldBody)
                .retrieve().toEntity(Map.class);
        assertThat(createdField.getStatusCode().is2xxSuccessful()).isTrue();
        waitForField(client, slug, collectionId, "title");

        // 2. Flip captureGeo on → NATS-driven refresh runs the lazy ALTER on every pod.
        Map<String, Object> patchBody = Map.of("data", Map.of(
                "type", "collections",
                "id", collectionId,
                "attributes", Map.of("captureGeo", true)));
        ResponseEntity<Map> patched = client.patch().uri("/" + slug + "/api/collections/" + collectionId)
                .contentType(MediaType.APPLICATION_JSON).body(patchBody)
                .retrieve().toEntity(Map.class);
        assertThat(patched.getStatusCode().is2xxSuccessful()).isTrue();

        try (Connection admin = openDbConnection()) {
            waitForGeoColumns(admin, collectionName);

            // 3. Write a record with the gateway-trusted geo headers (worker direct —
            //    the harness stands in for the gateway; city is percent-encoded UTF-8).
            Map<String, Object> recordBody = Map.of("data", Map.of(
                    "type", collectionName,
                    "attributes", Map.of("title", "from Lisbon")));
            ResponseEntity<Map> createdRecord = workerClient().post().uri("/api/" + collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-ID", tenantId)
                    .header("X-Tenant-Slug", slug)
                    .header("X-Geo-Country", "PT")
                    .header("X-Geo-Region", "Lisbon")
                    .header("X-Geo-City", "M%C3%BCnchen")
                    .header("X-Geo-Lat", "38.6979")
                    .header("X-Geo-Lon", "-9.4207")
                    .body(recordBody)
                    .retrieve().toEntity(Map.class);
            assertThat(createdRecord.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> recordData = (Map<String, Object>) createdRecord.getBody().get("data");
            String recordId = (String) recordData.get("id");

            // 4. The stamp round-trips through the read path as a parsed JSON object.
            ResponseEntity<Map> reread = workerClient().get()
                    .uri("/api/" + collectionName + "/" + recordId)
                    .header("X-Tenant-ID", tenantId)
                    .header("X-Tenant-Slug", slug)
                    .retrieve().toEntity(Map.class);
            Map<String, Object> attrs = (Map<String, Object>)
                    ((Map<String, Object>) reread.getBody().get("data")).get("attributes");
            Map<String, Object> createdGeo = (Map<String, Object>) attrs.get("createdGeo");
            assertThat(createdGeo)
                    .as("createdGeo JSONB persisted and round-trips")
                    .isNotNull()
                    .containsEntry("country", "PT")
                    .containsEntry("region", "Lisbon")
                    .containsEntry("city", "München");

            // 5. And it is real JSONB in Postgres, not text.
            assertThat(geoColumnCountry(admin, collectionName, recordId))
                    .isEqualTo("PT");
        }
    }

    /** Polls the fields list until the field has propagated to the in-memory definition. */
    @SuppressWarnings("unchecked")
    private void waitForField(RestClient client, String slug, String collectionId, String fieldName)
            throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            try {
                ResponseEntity<Map> fields = client.get()
                        .uri("/" + slug + "/api/fields?filter[collectionId][eq]=" + collectionId)
                        .retrieve().toEntity(Map.class);
                java.util.List<Map<String, Object>> data =
                        (java.util.List<Map<String, Object>>) fields.getBody().get("data");
                boolean present = data != null && data.stream().anyMatch(f -> {
                    Map<String, Object> a = (Map<String, Object>) f.get("attributes");
                    return a != null && fieldName.equals(a.get("name"));
                });
                if (present) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // not ready yet
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Field '" + fieldName + "' never propagated");
    }

    /** Polls until the lazy ALTER lands (NATS refresh timing). */
    private void waitForGeoColumns(Connection conn, String tableName) throws Exception {
        for (int i = 0; i < 30; i++) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_name = ? AND column_name IN ('created_geo', 'updated_geo')
                    """)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 2) {
                        return;
                    }
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("created_geo/updated_geo never appeared on " + tableName
                + " — captureGeo lazy ALTER did not run");
    }

    private String geoColumnCountry(Connection conn, String tableName, String recordId) throws Exception {
        // User tables may live in a tenant-specific schema — resolve it instead of
        // relying on the admin connection's search_path.
        String schema;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_schema FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = 'created_geo' LIMIT 1")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                schema = rs.getString(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT created_geo->>'country' FROM \"" + schema + "\".\"" + tableName
                        + "\" WHERE id = ?")) {
            ps.setString(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
