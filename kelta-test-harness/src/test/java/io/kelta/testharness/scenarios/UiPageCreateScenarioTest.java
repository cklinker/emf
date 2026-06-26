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
 * Creating a UI page through the real stack (gateway → worker → before-save hooks →
 * PhysicalTableStorageAdapter → Postgres).
 *
 * <p>Regression guard for the production 500 where a page create violated the
 * {@code ui_page.slug NOT NULL} constraint (V116): the builder / API / MCP payloads send
 * {@code name} + {@code path} but no slug, and nothing filled it. {@code UIPageSlugHook}
 * now derives the slug server-side. This scenario exercises the exact path against a real
 * Postgres with the live constraints — Mockito worker tests can't, since they have no DB.
 */
@DisplayName("UI Page Create Scenario")
class UiPageCreateScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("creates a page without a client-supplied slug (slug auto-derived from name)")
    void createsPageWithoutSlug() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        // Route must be live on the gateway before we POST.
        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/ui-pages", HttpStatus.OK, 20);

        // JSON:API create body — deliberately NO `slug` (mirrors the page-builder payload).
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "ui-pages",
                        "attributes", Map.of(
                                "name", "Test Dashboard",
                                "path", "/test-dashboard",
                                "title", "Test Dashboard",
                                "published", false,
                                "config", Map.of("layout", Map.of("type", "sidebar"), "components", List.of()))));

        ResponseEntity<Map> response = gatewayClientWithToken(token)
                .post()
                .uri("/" + slug + "/api/ui-pages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(Map.class);

        // Before the fix this was a 500 (StorageException ← null slug NOT NULL violation).
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("page create should succeed without a client slug")
                .isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).as("response carries the created resource").isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        Object derivedSlug = attributes.get("slug");
        assertThat(derivedSlug)
                .as("slug is auto-derived from the page name")
                .isInstanceOf(String.class);
        // Re-runs disambiguate (test-dashboard, test-dashboard-2, …) — match the stem.
        assertThat(((String) derivedSlug)).matches("test-dashboard(-\\d+)?");
    }
}
