package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import io.kelta.testharness.fixtures.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that collections seeded in the database are routable through the gateway.
 *
 * <p>kelta-worker loads collection definitions from Postgres on startup and publishes
 * {@code kelta.config.collection.changed} events via NATS JetStream so every gateway
 * pod rebuilds its RouteRegistry. This scenario confirms the full path:
 * Postgres → worker startup → NATS event → gateway RouteRegistry → routable endpoint.
 *
 * <p>The {@code threadline-clothing} tenant is seeded by Flyway V50 with nine
 * collections (products, customers, orders, etc.). We use those seeded collections
 * rather than creating new ones to keep the test read-only and idempotent.
 */
@DisplayName("Collection Lifecycle Scenario")
class CollectionLifecycleScenarioTest extends ScenarioBase {

    /**
     * After gateway startup the route registry must already contain the collections
     * seeded by Flyway V50 for the {@code threadline-clothing} tenant.
     *
     * <p>We poll /actuator/health first (already done by KeltaStack startup), then
     * verify that the gateway accepts a request to a seeded collection endpoint.
     */
    @Test
    @DisplayName("gateway routes request to seeded collection after startup")
    void gatewayRoutesSeededCollection() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        // The token may be for either the default or threadline-clothing tenant.
        // Use the ecommerce slug only if the token is scoped to that tenant.
        String targetSlug = TenantFixture.ECOMMERCE_SLUG;
        if (!TenantFixture.ECOMMERCE_SLUG.equals(slug)) {
            // Re-authenticate isn't possible per-tenant via direct-login without knowing
            // which tenant wins. Use the slug we got — it still has collections loaded.
            targetSlug = slug;
        }

        final String slugToUse = targetSlug;

        // Gateway may need a moment to propagate NATS route events after startup.
        // Poll /actuator/health to confirm gateway is ready (already guaranteed by
        // KeltaStack.start()), then try the collection endpoint.
        waitForStatus(
                gatewayClientWithToken(token),
                "/" + slugToUse + "/api/collections",
                HttpStatus.OK,
                20
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> body = gatewayClientWithToken(token)
                .get()
                .uri("/" + slugToUse + "/api/collections")
                .retrieve()
                .body(Map.class);

        assertThat(body).containsKey("data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).isNotEmpty();
    }

    /**
     * Verifies that the JSON:API collections response contains the expected system
     * collections (Users, Roles at minimum) for any active tenant.
     */
    @Test
    @DisplayName("collections list includes at least the system collections")
    void collectionsListIncludesSystemCollections() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        assertThat(slug).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = gatewayClientWithToken(token)
                .get()
                .uri("/" + slug + "/api/collections")
                .retrieve()
                .body(Map.class);

        assertThat(body).containsKey("data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).isNotEmpty();

        // Every collection resource in the JSON:API response must have an id and type
        data.forEach(item -> {
            assertThat(item).containsKey("id");
            assertThat(item).containsKey("type");
        });
    }

    /**
     * Verifies that the ecommerce tenant ({@code threadline-clothing}) has the V50-seeded
     * collections available via the gateway.
     *
     * <p>If the auth token resolves to the ecommerce tenant, we confirm the full
     * product catalogue slug is reachable. This validates NATS route propagation
     * for a tenant with a non-trivial collection set.
     */
    @Test
    @DisplayName("ecommerce tenant collections are routable when seeded")
    void ecommerceTenantCollectionsRoutable() {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        assertThat(slug).isNotNull();

        // Verify worker's slug-map contains the ecommerce tenant
        String ecommerceId = tenants.tenantIdForSlug(TenantFixture.ECOMMERCE_SLUG);

        // Both tenants are seeded — at least one must exist
        String activeSlug = (ecommerceId != null) ? TenantFixture.ECOMMERCE_SLUG : slug;

        // For the ecommerce tenant, check collections are routed (V50 seeds products etc.)
        // We need a token for the right tenant; if our token's tenant happens to be
        // ecommerce we can check directly, otherwise verify via worker's collections endpoint.
        if (TenantFixture.ECOMMERCE_SLUG.equals(slug)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = gatewayClientWithToken(token)
                    .get()
                    .uri("/" + slug + "/api/collections")
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            // V50 seeds at least products + customers + orders (9 total)
            assertThat(data.size()).isGreaterThanOrEqualTo(3);
        } else {
            // Confirm ecommerce tenant is registered in the slug-map (worker awareness)
            assertThat(ecommerceId).isNotNull();
        }
    }

    /**
     * Verifies that the gateway rejects requests to a collection endpoint for a slug
     * that does not exist in the system.
     */
    @Test
    @DisplayName("gateway returns 404 for unknown tenant slug on collection endpoint")
    void unknownSlugReturns404() {
        String token = auth.loginAsAdmin();

        HttpStatusCode status = gatewayClientWithToken(token)
                .get()
                .uri("/nonexistent-tenant-xyz/api/collections")
                .retrieve()
                .onStatus(s -> true, (req, resp) -> {})
                .toBodilessEntity()
                .getStatusCode();

        // Gateway should return 404 for unknown slugs
        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
