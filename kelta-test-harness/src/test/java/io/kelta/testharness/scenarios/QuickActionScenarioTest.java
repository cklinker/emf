package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick-action definitions through the real stack (gateway → worker → PhysicalTableStorageAdapter
 * → Postgres) against the {@code quick_action} table (V151) + its {@code quick-actions} system
 * collection.
 *
 * <p>Regression guard for the NOT-NULL columns + JSONB {@code config} + RLS on the generic dynamic
 * CRUD route that {@code useQuickActions} calls — the path Mockito worker tests can't reach.
 */
@DisplayName("Quick Action Scenario")
class QuickActionScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("creates a quick action and lists it by collection")
    @SuppressWarnings("unchecked")
    void createsAndListsQuickAction() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/quick-actions", HttpStatus.OK, 20);

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "quick-actions",
                        "attributes", Map.of(
                                "collectionName", "orders",
                                "label", "Mark Won",
                                "icon", "CheckCircle",
                                "actionType", "update_field",
                                "context", "record",
                                "sortOrder", 1,
                                "active", true,
                                "config", Map.of("type", "update_field", "fieldName", "stage", "setValue", "Won"))));

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/quick-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("quick action create should succeed against real Postgres").isTrue();

        ResponseEntity<Map> listed = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/quick-actions?filter[collectionName][eq]=orders&filter[active][eq]=true")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> actions = (List<Map<String, Object>>) listed.getBody().get("data");
        assertThat(actions).as("the created action is returned for its collection").hasSize(1);
    }
}
