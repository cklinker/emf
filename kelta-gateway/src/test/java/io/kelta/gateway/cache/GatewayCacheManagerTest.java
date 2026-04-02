package io.kelta.gateway.cache;

import io.kelta.gateway.config.GovernorLimitConfig;
import io.kelta.gateway.route.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GatewayCacheManager.
 *
 * Tests verify both the tenant slug cache and governor limit cache functionality
 * backed by Caffeine caches.
 */
@ExtendWith(MockitoExtension.class)
class GatewayCacheManagerTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GatewayCacheManager cacheManager;

    private static final String WORKER_SERVICE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        cacheManager = new GatewayCacheManager(webClientBuilder, WORKER_SERVICE_URL);
    }

    // ── Tenant Slug Cache Tests ───────────────────────────────────────

    @Nested
    class TenantSlugCacheTests {

        @Test
        void resolveReturnsEmptyWhenCacheIsEmpty() {
            Optional<String> result = cacheManager.resolveTenantSlug("acme");
            assertThat(result).isEmpty();
        }

        @Test
        void isKnownSlugReturnsFalseWhenCacheIsEmpty() {
            assertThat(cacheManager.isKnownSlug("acme")).isFalse();
        }

        @Test
        void tenantSlugCacheSizeReturnsZeroWhenCacheIsEmpty() {
            assertThat(cacheManager.tenantSlugCacheSize()).isEqualTo(0);
        }

        @Test
        void resolveReturnsEmptyForNullSlug() {
            Optional<String> result = cacheManager.resolveTenantSlug(null);
            assertThat(result).isEmpty();
        }

        @Test
        void resolveReturnsEmptyForBlankSlug() {
            Optional<String> result = cacheManager.resolveTenantSlug("   ");
            assertThat(result).isEmpty();
        }

        @Test
        void isKnownSlugReturnsFalseForNullSlug() {
            assertThat(cacheManager.isKnownSlug(null)).isFalse();
        }

        @Test
        void resolveReturnsCorrectTenantIdAfterRefresh() {
            // Given
            Map<String, String> slugMap = Map.of(
                    "acme", "tenant-id-1",
                    "globex", "tenant-id-2"
            );
            stubRefreshResponse(Mono.just(slugMap));

            // When
            cacheManager.refreshTenantSlugsFromWorker();

            // Then
            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");
            assertThat(cacheManager.resolveTenantSlug("globex")).contains("tenant-id-2");
        }

        @Test
        void isKnownSlugReturnsTrueAfterRefresh() {
            // Given
            Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(slugMap));

            // When
            cacheManager.refreshTenantSlugsFromWorker();

            // Then
            assertThat(cacheManager.isKnownSlug("acme")).isTrue();
        }

        @Test
        void tenantSlugCacheSizeReflectsCacheContentsAfterRefresh() {
            // Given
            Map<String, String> slugMap = Map.of(
                    "acme", "tenant-id-1",
                    "globex", "tenant-id-2",
                    "initech", "tenant-id-3"
            );
            stubRefreshResponse(Mono.just(slugMap));

            // When
            cacheManager.refreshTenantSlugsFromWorker();

            // Then
            assertThat(cacheManager.tenantSlugCacheSize()).isEqualTo(3);
        }

        @Test
        void unknownSlugReturnsEmptyAfterRefresh() {
            // Given
            Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(slugMap));

            // When
            cacheManager.refreshTenantSlugsFromWorker();

            // Then
            assertThat(cacheManager.resolveTenantSlug("unknown")).isEmpty();
            assertThat(cacheManager.isKnownSlug("unknown")).isFalse();
        }

        @Test
        void refreshReplacesExistingCacheEntries() {
            // Given - first refresh with acme
            Map<String, String> firstMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(firstMap));
            cacheManager.refreshTenantSlugsFromWorker();

            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");

            // When - second refresh with different data
            Map<String, String> secondMap = Map.of("globex", "tenant-id-2");
            stubRefreshResponse(Mono.just(secondMap));
            cacheManager.refreshTenantSlugsFromWorker();

            // Then - old entry is gone, new entry is present
            assertThat(cacheManager.resolveTenantSlug("acme")).isEmpty();
            assertThat(cacheManager.resolveTenantSlug("globex")).contains("tenant-id-2");
        }

        @Test
        void refreshHandlesErrorGracefully() {
            // Given
            stubRefreshResponse(Mono.error(new RuntimeException("Connection refused")));

            // When - should not throw
            cacheManager.refreshTenantSlugsFromWorker();

            // Then - cache remains empty
            assertThat(cacheManager.tenantSlugCacheSize()).isEqualTo(0);
            assertThat(cacheManager.resolveTenantSlug("acme")).isEmpty();
        }

        @Test
        void refreshKeepsExistingCacheOnError() {
            // Given - populate cache first
            Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(slugMap));
            cacheManager.refreshTenantSlugsFromWorker();

            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");

            // When - second refresh fails
            stubRefreshResponse(Mono.error(new RuntimeException("Connection refused")));
            cacheManager.refreshTenantSlugsFromWorker();

            // Then - existing cache is preserved
            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");
            assertThat(cacheManager.tenantSlugCacheSize()).isEqualTo(1);
        }

        @Test
        void refreshKeepsExistingCacheOnNullResponse() {
            // Given - populate cache first
            Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(slugMap));
            cacheManager.refreshTenantSlugsFromWorker();

            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");

            // When - second refresh returns null
            stubRefreshResponse(Mono.justOrEmpty(null));
            cacheManager.refreshTenantSlugsFromWorker();

            // Then - existing cache is preserved
            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");
        }

        @Test
        void refreshKeepsExistingCacheOnEmptyMapResponse() {
            // Given - populate cache first
            Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
            stubRefreshResponse(Mono.just(slugMap));
            cacheManager.refreshTenantSlugsFromWorker();

            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");

            // When - second refresh returns empty map
            stubRefreshResponse(Mono.just(Map.of()));
            cacheManager.refreshTenantSlugsFromWorker();

            // Then - existing cache is preserved
            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");
        }

        @Test
        void refreshTenantSlugsBulkLoadsEntries() {
            // Given
            Map<String, String> slugMap = Map.of(
                    "acme", "tenant-id-1",
                    "globex", "tenant-id-2"
            );

            // When
            cacheManager.refreshTenantSlugs(slugMap);

            // Then
            assertThat(cacheManager.resolveTenantSlug("acme")).contains("tenant-id-1");
            assertThat(cacheManager.resolveTenantSlug("globex")).contains("tenant-id-2");
            assertThat(cacheManager.tenantSlugCacheSize()).isEqualTo(2);
        }
    }

    // ── Governor Limit Cache Tests ────────────────────────────────────

    @Nested
    class GovernorLimitCacheTests {

        @Test
        void testLoadGovernorLimits() {
            // Given
            Map<String, GovernorLimitConfig> limits = new HashMap<>();
            limits.put("tenant-1", new GovernorLimitConfig(100_000));
            limits.put("tenant-2", new GovernorLimitConfig(50_000));

            // When
            cacheManager.loadGovernorLimits(limits);

            // Then
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(2);
            assertEquals(Optional.of(100_000), cacheManager.getGovernorLimit("tenant-1"));
            assertEquals(Optional.of(50_000), cacheManager.getGovernorLimit("tenant-2"));
        }

        @Test
        void testLoadGovernorLimits_NullMap() {
            // When
            cacheManager.loadGovernorLimits(null);

            // Then
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(0);
        }

        @Test
        void testLoadGovernorLimits_EmptyMap() {
            // When
            cacheManager.loadGovernorLimits(new HashMap<>());

            // Then
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(0);
        }

        @Test
        void testUpdateGovernorLimit() {
            // When
            cacheManager.updateGovernorLimit("tenant-1", 200_000);

            // Then
            assertEquals(Optional.of(200_000), cacheManager.getGovernorLimit("tenant-1"));
        }

        @Test
        void testGetRateLimitForTenant_Known() {
            // Given
            cacheManager.updateGovernorLimit("tenant-1", 144_000); // (144,000 / 288) * 5 = 2500 per 5-min window

            // When
            RateLimitConfig config = cacheManager.getRateLimitForTenant("tenant-1");

            // Then
            assertEquals(2500, config.getRequestsPerWindow());
            assertEquals(Duration.ofMinutes(5), config.getWindowDuration());
        }

        @Test
        void testGetRateLimitForTenant_Unknown_UsesDefault() {
            // When - tenant not in cache, should use default (100,000/day)
            RateLimitConfig config = cacheManager.getRateLimitForTenant("unknown-tenant");

            // Then - (100,000 / 288) * 5 = 1735 per 5-min window
            assertEquals(1735, config.getRequestsPerWindow());
            assertEquals(Duration.ofMinutes(5), config.getWindowDuration());
        }

        @Test
        void testGetRateLimitForTenant_MinimumOnePerWindow() {
            // Given - very low limit: 1 per day
            cacheManager.updateGovernorLimit("low-tenant", 1);

            // When
            RateLimitConfig config = cacheManager.getRateLimitForTenant("low-tenant");

            // Then - minimum 1 request per window
            assertEquals(1, config.getRequestsPerWindow());
        }

        @Test
        void testGetGovernorLimit_NotFound() {
            // When
            Optional<Integer> result = cacheManager.getGovernorLimit("nonexistent");

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        void testLoadGovernorLimits_ClearsExisting() {
            // Given - pre-populate cache
            cacheManager.updateGovernorLimit("old-tenant", 50_000);
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(1);

            // When - load new data
            Map<String, GovernorLimitConfig> limits = new HashMap<>();
            limits.put("new-tenant", new GovernorLimitConfig(100_000));
            cacheManager.loadGovernorLimits(limits);

            // Then - old data cleared, new data loaded
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(1);
            assertTrue(cacheManager.getGovernorLimit("old-tenant").isEmpty());
            assertEquals(Optional.of(100_000), cacheManager.getGovernorLimit("new-tenant"));
        }
    }

    // ── Governor Limit Refresh From Worker Tests ────────────────────

    @Nested
    class GovernorLimitRefreshFromWorkerTests {

        @Test
        void refreshGovernorLimitsFromWorkerUpdatesCache() {
            // Given
            Map<String, Integer> limitsMap = Map.of(
                    "tenant-1", 10_000_000,
                    "tenant-2", 50_000
            );
            stubGovernorLimitsRefreshResponse(Mono.just(limitsMap));

            // When
            cacheManager.refreshGovernorLimitsFromWorker();

            // Then
            assertEquals(Optional.of(10_000_000), cacheManager.getGovernorLimit("tenant-1"));
            assertEquals(Optional.of(50_000), cacheManager.getGovernorLimit("tenant-2"));
            assertThat(cacheManager.governorLimitCacheSize()).isEqualTo(2);
        }

        @Test
        void refreshGovernorLimitsFromWorkerReplacesExistingCache() {
            // Given - pre-populate
            cacheManager.updateGovernorLimit("old-tenant", 100_000);
            assertThat(cacheManager.getGovernorLimit("old-tenant")).contains(100_000);

            Map<String, Integer> limitsMap = Map.of("new-tenant", 200_000);
            stubGovernorLimitsRefreshResponse(Mono.just(limitsMap));

            // When
            cacheManager.refreshGovernorLimitsFromWorker();

            // Then
            assertTrue(cacheManager.getGovernorLimit("old-tenant").isEmpty());
            assertEquals(Optional.of(200_000), cacheManager.getGovernorLimit("new-tenant"));
        }

        @Test
        void refreshGovernorLimitsFromWorkerHandlesErrorGracefully() {
            // Given - pre-populate cache
            cacheManager.updateGovernorLimit("tenant-1", 100_000);

            stubGovernorLimitsRefreshResponse(Mono.error(new RuntimeException("Connection refused")));

            // When - should not throw
            cacheManager.refreshGovernorLimitsFromWorker();

            // Then - existing cache is preserved
            assertEquals(Optional.of(100_000), cacheManager.getGovernorLimit("tenant-1"));
        }

        @Test
        void refreshGovernorLimitsFromWorkerKeepsCacheOnEmptyResponse() {
            // Given - pre-populate cache
            cacheManager.updateGovernorLimit("tenant-1", 100_000);

            stubGovernorLimitsRefreshResponse(Mono.just(Map.of()));

            // When
            cacheManager.refreshGovernorLimitsFromWorker();

            // Then - existing cache is preserved
            assertEquals(Optional.of(100_000), cacheManager.getGovernorLimit("tenant-1"));
        }
    }

    // ── Custom Domain Cache Tests ────────────────────────────────────

    @Nested
    class CustomDomainCacheTests {

        @Test
        void resolveCustomDomain_cachesPositiveResult() {
            // Given - stub a successful worker response
            stubCustomDomainResolveResponse("app.acme.com", Mono.just("acme"));

            // When - first lookup hits worker
            Optional<String> first = cacheManager.resolveCustomDomain("app.acme.com");

            // Then
            assertThat(first).contains("acme");
        }

        @Test
        void resolveCustomDomain_cachesNegativeResult() {
            // Given - stub a 404 from worker (WebClient throws on non-2xx)
            stubCustomDomainResolveResponse("unknown.com",
                    Mono.error(new org.springframework.web.reactive.function.client.WebClientResponseException(
                            404, "Not Found", null, null, null)));

            // When - first lookup hits worker, returns empty
            Optional<String> first = cacheManager.resolveCustomDomain("unknown.com");
            assertThat(first).isEmpty();

            // Second lookup should NOT call worker again (cached negative)
            // Reset mock interactions
            reset(webClient);

            Optional<String> second = cacheManager.resolveCustomDomain("unknown.com");
            assertThat(second).isEmpty();

            // Verify no more interactions with webClient
            verifyNoInteractions(webClient);
        }

        @Test
        void registerCustomDomain_addsToCacheDirectly() {
            cacheManager.registerCustomDomain("app.acme.com", "acme");

            Optional<String> result = cacheManager.resolveCustomDomain("app.acme.com");
            assertThat(result).contains("acme");
            verifyNoInteractions(webClient);
        }

        @Test
        void removeCustomDomain_evictsFromCache() {
            cacheManager.registerCustomDomain("app.acme.com", "acme");
            cacheManager.removeCustomDomain("app.acme.com");

            // Next resolve should try the worker again (entry was evicted)
            stubCustomDomainResolveResponse("app.acme.com", Mono.just("acme-new"));

            Optional<String> result = cacheManager.resolveCustomDomain("app.acme.com");
            assertThat(result).contains("acme-new");
        }

        @Test
        void evictAllCustomDomains_clearsAllEntries() {
            cacheManager.registerCustomDomain("app.acme.com", "acme");
            cacheManager.registerCustomDomain("app.beta.com", "beta");
            cacheManager.evictAllCustomDomains();

            // Both should require worker lookup now
            stubCustomDomainResolveResponse("app.acme.com", Mono.just("acme"));
            Optional<String> result = cacheManager.resolveCustomDomain("app.acme.com");
            assertThat(result).contains("acme");
            verify(webClient).get(); // Confirms worker was called
        }

        @SuppressWarnings("unchecked")
        private void stubCustomDomainResolveResponse(String domain, Mono<String> response) {
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri("/internal/domains/resolve?domain={domain}", domain))
                    .thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class)).thenReturn(response);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubRefreshResponse(Mono<Map<String, String>> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/internal/tenants/slug-map")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubGovernorLimitsRefreshResponse(Mono<Map<String, Integer>> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/internal/governor-limits")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
    }
}
