package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Destructive schema-migration execute through the real stack (gateway → worker →
 * SchemaMigrationEngine → per-tenant Postgres). Exercises the highest-stakes path in the codebase:
 * a live {@code ALTER TABLE ... DROP COLUMN} on tenant data.
 *
 * <p>Flow: create a user collection with a field, snapshot it (v1), add a second field + a record,
 * then execute a rollback to v1. Asserts the added field's metadata row and physical column are
 * gone and the table still serves reads/writes. This is the regression guard the DB-constraint
 * test-gap lesson demands — Mockito worker tests cannot exercise real destructive DDL.
 */
@DisplayName("Schema Migration Scenario")
class SchemaMigrationScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("execute rolls a collection back to a prior version, dropping the added column + its data")
    @SuppressWarnings("unchecked")
    void executeRollbackDropsColumn() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        RestClient client = gatewayClientWithToken(token);

        String collectionName = "migrtest";

        // Collections route is live for the tenant.
        waitForStatus(client, "/" + slug + "/api/collections", HttpStatus.OK, 20);

        // 1. Create a user collection.
        Map<String, Object> collectionBody = Map.of("data", Map.of(
                "type", "collections",
                "attributes", Map.of(
                        "name", collectionName,
                        "displayName", "Migration Test",
                        "tenantScoped", true)));
        ResponseEntity<Map> createdCollection = client.post().uri("/" + slug + "/api/collections")
                .contentType(MediaType.APPLICATION_JSON).body(collectionBody)
                .retrieve().toEntity(Map.class);
        assertThat(createdCollection.getStatusCode().is2xxSuccessful()).isTrue();
        String collectionId = (String) ((Map<String, Object>) createdCollection.getBody().get("data")).get("id");
        assertThat(collectionId).isNotBlank();

        // The dynamic route for the new collection propagates via NATS — wait for it.
        waitForStatus(client, "/" + slug + "/api/" + collectionName, HttpStatus.OK, 30);

        // 2. Add the original field "title".
        addField(client, slug, collectionId, "title");
        waitForField(client, slug, collectionId, "title", true);

        // 3. Snapshot the current schema as version 1 (title + audit fields, no "extra").
        Map<String, Object> snapshotBody = Map.of("collectionId", collectionId);
        ResponseEntity<Map> snapshot = client.post().uri("/" + slug + "/api/migrations/snapshot")
                .contentType(MediaType.APPLICATION_JSON).body(snapshotBody)
                .retrieve().toEntity(Map.class);
        assertThat(snapshot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Integer v1 = ((Number) snapshot.getBody().get("version")).intValue();
        assertThat(v1).isEqualTo(1);

        // 4. Add the "extra" field, then create a record populating both fields.
        addField(client, slug, collectionId, "extra");
        waitForField(client, slug, collectionId, "extra", true);

        Map<String, Object> recordBody = Map.of("data", Map.of(
                "type", collectionName,
                "attributes", Map.of("title", "keep-me", "extra", "drop-me")));
        ResponseEntity<Map> createdRecord = client.post().uri("/" + slug + "/api/" + collectionName)
                .contentType(MediaType.APPLICATION_JSON).body(recordBody)
                .retrieve().toEntity(Map.class);
        assertThat(createdRecord.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> recordData = (Map<String, Object>) createdRecord.getBody().get("data");
        String recordId = (String) recordData.get("id");
        assertThat((Map<String, Object>) recordData.get("attributes")).containsKey("extra");

        // 5. Execute the destructive rollback to v1 (drops "extra").
        Map<String, Object> executeBody = Map.of("collectionId", collectionId, "targetVersion", v1);
        ResponseEntity<Map> execute = client.post().uri("/" + slug + "/api/migrations/execute")
                .contentType(MediaType.APPLICATION_JSON).body(executeBody)
                .retrieve().toEntity(Map.class);
        assertThat(execute.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(execute.getBody()).containsEntry("status", "completed");
        assertThat(execute.getBody().get("toVersion")).isEqualTo(1);

        // 6. The "extra" field metadata row is gone.
        waitForField(client, slug, collectionId, "extra", false);

        // 7. The dropped column's data is gone: re-read the record — "title" survives, "extra" does not.
        ResponseEntity<Map> reread = client.get().uri("/" + slug + "/api/" + collectionName + "/" + recordId)
                .retrieve().toEntity(Map.class);
        assertThat(reread.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> attrs = (Map<String, Object>) ((Map<String, Object>) reread.getBody().get("data"))
                .get("attributes");
        assertThat(attrs).as("title survives the rollback").containsKey("title");
        assertThat(attrs).as("extra column was physically dropped").doesNotContainKey("extra");

        // 8. Data still behaves: the table serves a fresh write against the rolled-back schema.
        Map<String, Object> afterBody = Map.of("data", Map.of(
                "type", collectionName,
                "attributes", Map.of("title", "after-rollback")));
        ResponseEntity<Map> afterCreate = client.post().uri("/" + slug + "/api/" + collectionName)
                .contentType(MediaType.APPLICATION_JSON).body(afterBody)
                .retrieve().toEntity(Map.class);
        assertThat(afterCreate.getStatusCode().is2xxSuccessful())
                .as("collection still accepts writes after destructive DDL").isTrue();
    }

    private void addField(RestClient client, String slug, String collectionId, String fieldName) {
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "fields",
                "attributes", Map.of(
                        "collectionId", collectionId,
                        "name", fieldName,
                        "type", "STRING")));
        ResponseEntity<Map> response = client.post().uri("/" + slug + "/api/fields")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("field '%s' create should succeed", fieldName).isTrue();
    }

    /** Polls the fields list until {@code fieldName} is present (or absent, when {@code shouldExist} is false). */
    @SuppressWarnings("unchecked")
    private void waitForField(RestClient client, String slug, String collectionId,
                              String fieldName, boolean shouldExist) {
        for (int i = 0; i < 30; i++) {
            try {
                ResponseEntity<Map> fields = client.get()
                        .uri("/" + slug + "/api/fields?filter[collectionId][eq]=" + collectionId)
                        .retrieve().toEntity(Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) fields.getBody().get("data");
                boolean present = data != null && data.stream().anyMatch(f -> {
                    Map<String, Object> a = (Map<String, Object>) f.get("attributes");
                    return a != null && fieldName.equals(a.get("name"));
                });
                if (present == shouldExist) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // not ready yet
            }
            sleep();
        }
        throw new AssertionError("Field '" + fieldName + "' expected "
                + (shouldExist ? "present" : "absent") + " but timed out");
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
