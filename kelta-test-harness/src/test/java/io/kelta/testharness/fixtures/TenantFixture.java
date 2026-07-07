package io.kelta.testharness.fixtures;

import io.kelta.testharness.KeltaStack;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Resolves tenant slugs and manages tenant-scoped test state.
 *
 * <p>The Flyway baseline seeds the {@code default} tenant. The
 * {@code threadline-clothing} tenant is provisioned at harness startup by
 * {@link EcommerceSeedFixture} through the admin API (replacing the old Flyway V50
 * demo seed) — both are ACTIVE by the time scenarios run.
 */
public final class TenantFixture {

    /** Seeded by the Flyway baseline — no user collections, used for isolation tests. */
    public static final String DEFAULT_SLUG = "default";

    /** Provisioned at startup by {@link EcommerceSeedFixture} — has customers, orders, products. */
    public static final String ECOMMERCE_SLUG = "threadline-clothing";

    private final RestClient workerClient;

    public TenantFixture() {
        this.workerClient = RestClient.builder()
                .baseUrl(KeltaStack.workerBaseUrl())
                .build();
    }

    /**
     * Returns the tenant ID for the given slug by querying the worker's internal
     * slug-map endpoint. Returns {@code null} if the slug is not found.
     */
    @SuppressWarnings("unchecked")
    public String tenantIdForSlug(String slug) {
        Map<String, String> slugMap = workerClient.get()
                .uri("/internal/tenants/slug-map")
                .retrieve()
                .body(Map.class);
        return slugMap != null ? slugMap.get(slug) : null;
    }

    /**
     * Returns the slug for the given tenant ID, or {@code null} if not found.
     */
    @SuppressWarnings("unchecked")
    public String slugForTenantId(String tenantId) {
        Map<String, String> slugMap = workerClient.get()
                .uri("/internal/tenants/slug-map")
                .retrieve()
                .body(Map.class);
        if (slugMap == null) return null;
        return slugMap.entrySet().stream()
                .filter(e -> tenantId.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
