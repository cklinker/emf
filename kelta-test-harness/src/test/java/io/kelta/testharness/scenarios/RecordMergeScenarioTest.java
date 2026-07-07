package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duplicate-record merge through the real stack (gateway → worker → QueryEngine →
 * PhysicalTableStorageAdapter → Postgres), exercising {@code POST /api/collections/{name}/merge}.
 *
 * <p>Regression guard for the write path Mockito worker tests can't reach: real inbound-FK
 * re-parenting and a real cascade-safe delete on Postgres. It merges two {@code customers} on the
 * fixture-seeded {@code threadline-clothing} tenant, with a real {@code orders.customer} LOOKUP row
 * pointing at the duplicate, and proves:
 * <ol>
 *   <li>the order's FK is re-pointed from the duplicate to the master (not left dangling, not
 *       {@code SET NULL}ed by the delete), and</li>
 *   <li>the duplicate customer is actually gone while the master survives.</li>
 * </ol>
 * (See the DB-constraint-test-gap lesson — a Mockito test would happily pass even if the FK
 * re-parent SQL never ran.)
 */
@DisplayName("Record Merge Scenario")
class RecordMergeScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("merges duplicates: re-parents inbound FKs onto the master, then deletes them")
    @SuppressWarnings("unchecked")
    void mergesReparentsAndDeletes() {
        // Force the ecommerce tenant so the seeded customers/orders collections + FK are present.
        String slug = TenantFixture.ECOMMERCE_SLUG;
        String token = auth.loginAsAdmin(slug);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/customers", HttpStatus.OK, 20);

        // Two duplicate customers: the master we keep and the duplicate we fold in. They share a
        // name but need distinct emails — the fixture-seeded `customers.email` field is uniquely
        // constrained (the merge takes record ids directly, so an exact match-field value isn't
        // required here).
        String suffix = Long.toHexString(System.nanoTime());
        String masterId = createCustomer(token, slug, "dupe-master-" + suffix + "@example.com", "Dupe", "Master");
        String dupId = createCustomer(token, slug, "dupe-dup-" + suffix + "@example.com", "Dupe", "Duplicate");
        assertThat(masterId).isNotBlank();
        assertThat(dupId).isNotBlank().isNotEqualTo(masterId);

        // A real inbound LOOKUP: an order whose customer points at the duplicate.
        String orderId = createOrder(token, slug, dupId);

        // Merge the duplicate into the master.
        Map<String, Object> mergeBody = Map.of(
                "masterId", masterId,
                "duplicateIds", List.of(dupId));
        ResponseEntity<Map> merged = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/collections/customers/merge")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mergeBody)
                .retrieve().toEntity(Map.class);
        assertThat(merged.getStatusCode().is2xxSuccessful())
                .as("merge should succeed against real Postgres").isTrue();

        Map<String, Object> mergeResult = merged.getBody();
        assertThat((List<String>) mergeResult.get("deletedIds"))
                .as("the duplicate is reported deleted").containsExactly(dupId);
        assertThat(((Number) mergeResult.get("reparentedRecords")).intValue())
                .as("the order FK was re-parented").isGreaterThanOrEqualTo(1);

        // The order now points at the master — proves the inbound FK was re-parented (not SET NULL).
        // A relationship field is serialized as a JSON:API relationship, not an `attributes` value,
        // so assert against the stored FK column via a filter query (serialization-agnostic).
        ResponseEntity<Map> byMaster = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/orders?filter[customer][eq]=" + masterId)
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> masterOrders = (List<Map<String, Object>>) byMaster.getBody().get("data");
        assertThat(masterOrders)
                .as("order re-parented from duplicate to master")
                .anySatisfy(o -> assertThat(o.get("id")).isEqualTo(orderId));

        // ...and no order still points at the deleted duplicate.
        ResponseEntity<Map> byDup = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/orders?filter[customer][eq]=" + dupId)
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> dupOrders = (List<Map<String, Object>>) byDup.getBody().get("data");
        assertThat(dupOrders)
                .as("no order left pointing at the merged-away duplicate")
                .noneSatisfy(o -> assertThat(o.get("id")).isEqualTo(orderId));

        // The duplicate is gone; the master survives.
        assertThat(recordStatus(token, slug, "customers", dupId))
                .as("duplicate customer deleted").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(recordStatus(token, slug, "customers", masterId))
                .as("master customer survives").isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private String createCustomer(String token, String slug, String email, String first, String last) {
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "customers",
                        "attributes", Map.of(
                                "email", email,
                                "first_name", first,
                                "last_name", last)));
        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        return (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
    }

    @SuppressWarnings("unchecked")
    private String createOrder(String token, String slug, String customerId) {
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "orders",
                        "attributes", Map.of(
                                "customer", customerId,
                                "status", "pending",
                                "order_date", "2026-01-15T10:00:00Z",
                                "subtotal", 99.99,
                                "tax_amount", 8.00,
                                "total_amount", 107.99)));
        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("order create should succeed").isTrue();
        return (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
    }

    private HttpStatusCode recordStatus(String token, String slug, String collection, String id) {
        return gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/" + collection + "/" + id)
                .retrieve()
                .onStatus(s -> true, (req, resp) -> {})
                .toBodilessEntity()
                .getStatusCode();
    }
}
