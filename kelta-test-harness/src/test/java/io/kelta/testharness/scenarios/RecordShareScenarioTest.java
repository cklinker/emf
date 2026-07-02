package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Manual record sharing through the real stack (gateway → worker → PhysicalTableStorageAdapter
 * → Postgres) against the {@code record_share} table (V150) + its {@code record-shares} system
 * collection.
 *
 * <p>Regression guard for the whole path Mockito worker tests can't reach: the NOT-NULL columns,
 * the {@code uq_record_share} UNIQUE constraint, RLS tenant isolation, and the generic dynamic
 * CRUD route the record-detail Sharing panel calls. (See the DB-constraint-test-gap lesson.)
 */
@DisplayName("Record Share Scenario")
class RecordShareScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("creates + lists a record share, and enforces the per-target UNIQUE constraint")
    @SuppressWarnings("unchecked")
    void createsAndListsShare() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        // Route must be live on the gateway before we POST.
        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/record-shares", HttpStatus.OK, 20);

        // A real collection id to reference (collectionId is a master-detail to `collections`).
        ResponseEntity<Map> collections = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/collections")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> collectionData = (List<Map<String, Object>>) collections.getBody().get("data");
        assertThat(collectionData).as("tenant has at least one collection").isNotEmpty();
        String collectionId = (String) collectionData.get(0).get("id");
        String recordId = "rec-share-001";

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "record-shares",
                        "attributes", Map.of(
                                "collectionId", collectionId,
                                "recordId", recordId,
                                "sharedWithId", "target-user-1",
                                "sharedWithType", "USER",
                                "accessLevel", "READ",
                                "reason", "harness scenario")));

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/record-shares")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("share create should succeed against real Postgres").isTrue();

        // List it back via the exact Sharing-panel query.
        ResponseEntity<Map> listed = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/record-shares?filter[collectionId][eq]=" + collectionId
                        + "&filter[recordId][eq]=" + recordId)
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> shares = (List<Map<String, Object>>) listed.getBody().get("data");
        assertThat(shares).as("the created share is returned by the record query").hasSize(1);

        // UNIQUE (tenant, collection, record, shared_with) — a duplicate must be a 4xx, not a 500.
        // RestClient throws HttpClientErrorException on 4xx (a 500 would be HttpServerErrorException).
        assertThatThrownBy(() -> gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/record-shares")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class))
                .as("duplicate share is a 4xx (unique constraint), not a 500")
                .isInstanceOf(HttpClientErrorException.class);
    }
}
