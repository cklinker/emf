package com.emf.gateway.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantSlugCache.
 *
 * Tests verify that:
 * - resolve() returns Optional.empty() when cache is empty
 * - isKnownSlug() returns false when cache is empty
 * - After refresh(), resolve() returns correct tenantId for known slugs
 * - After refresh(), isKnownSlug() returns true for known slugs
 * - Unknown slugs return Optional.empty() even after refresh
 * - Errors from the worker endpoint are handled gracefully
 */
@ExtendWith(MockitoExtension.class)
class TenantSlugCacheTest {

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

    private TenantSlugCache cache;

    private static final String WORKER_SERVICE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        cache = new TenantSlugCache(webClientBuilder, WORKER_SERVICE_URL);
    }

    // --- Empty cache ---

    @Test
    void resolveReturnsEmptyWhenCacheIsEmpty() {
        Optional<String> result = cache.resolve("acme");
        assertThat(result).isEmpty();
    }

    @Test
    void isKnownSlugReturnsFalseWhenCacheIsEmpty() {
        assertThat(cache.isKnownSlug("acme")).isFalse();
    }

    @Test
    void sizeReturnsZeroWhenCacheIsEmpty() {
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void resolveReturnsEmptyForNullSlug() {
        Optional<String> result = cache.resolve(null);
        assertThat(result).isEmpty();
    }

    @Test
    void resolveReturnsEmptyForBlankSlug() {
        Optional<String> result = cache.resolve("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void isKnownSlugReturnsFalseForNullSlug() {
        assertThat(cache.isKnownSlug(null)).isFalse();
    }

    // --- After successful refresh ---

    @Test
    void resolveReturnsCorrectTenantIdAfterRefresh() {
        // Given
        Map<String, String> slugMap = Map.of(
                "acme", "tenant-id-1",
                "globex", "tenant-id-2"
        );
        stubRefreshResponse(Mono.just(slugMap));

        // When
        cache.refresh();

        // Then
        assertThat(cache.resolve("acme")).contains("tenant-id-1");
        assertThat(cache.resolve("globex")).contains("tenant-id-2");
    }

    @Test
    void isKnownSlugReturnsTrueAfterRefresh() {
        // Given
        Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(slugMap));

        // When
        cache.refresh();

        // Then
        assertThat(cache.isKnownSlug("acme")).isTrue();
    }

    @Test
    void sizeReflectsCacheContentsAfterRefresh() {
        // Given
        Map<String, String> slugMap = Map.of(
                "acme", "tenant-id-1",
                "globex", "tenant-id-2",
                "initech", "tenant-id-3"
        );
        stubRefreshResponse(Mono.just(slugMap));

        // When
        cache.refresh();

        // Then
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    void unknownSlugReturnsEmptyAfterRefresh() {
        // Given
        Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(slugMap));

        // When
        cache.refresh();

        // Then
        assertThat(cache.resolve("unknown")).isEmpty();
        assertThat(cache.isKnownSlug("unknown")).isFalse();
    }

    @Test
    void refreshReplacesExistingCacheEntries() {
        // Given - first refresh with acme
        Map<String, String> firstMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(firstMap));
        cache.refresh();

        assertThat(cache.resolve("acme")).contains("tenant-id-1");

        // When - second refresh with different data
        Map<String, String> secondMap = Map.of("globex", "tenant-id-2");
        stubRefreshResponse(Mono.just(secondMap));
        cache.refresh();

        // Then - old entry is gone, new entry is present
        assertThat(cache.resolve("acme")).isEmpty();
        assertThat(cache.resolve("globex")).contains("tenant-id-2");
    }

    // --- Error handling ---

    @Test
    void refreshHandlesErrorGracefully() {
        // Given
        stubRefreshResponse(Mono.error(new RuntimeException("Connection refused")));

        // When - should not throw
        cache.refresh();

        // Then - cache remains empty
        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.resolve("acme")).isEmpty();
    }

    @Test
    void refreshKeepsExistingCacheOnError() {
        // Given - populate cache first
        Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(slugMap));
        cache.refresh();

        assertThat(cache.resolve("acme")).contains("tenant-id-1");

        // When - second refresh fails
        stubRefreshResponse(Mono.error(new RuntimeException("Connection refused")));
        cache.refresh();

        // Then - existing cache is preserved
        assertThat(cache.resolve("acme")).contains("tenant-id-1");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void refreshKeepsExistingCacheOnNullResponse() {
        // Given - populate cache first
        Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(slugMap));
        cache.refresh();

        assertThat(cache.resolve("acme")).contains("tenant-id-1");

        // When - second refresh returns null
        stubRefreshResponse(Mono.justOrEmpty(null));
        cache.refresh();

        // Then - existing cache is preserved
        assertThat(cache.resolve("acme")).contains("tenant-id-1");
    }

    @Test
    void refreshKeepsExistingCacheOnEmptyMapResponse() {
        // Given - populate cache first
        Map<String, String> slugMap = Map.of("acme", "tenant-id-1");
        stubRefreshResponse(Mono.just(slugMap));
        cache.refresh();

        assertThat(cache.resolve("acme")).contains("tenant-id-1");

        // When - second refresh returns empty map
        stubRefreshResponse(Mono.just(Map.of()));
        cache.refresh();

        // Then - existing cache is preserved
        assertThat(cache.resolve("acme")).contains("tenant-id-1");
    }

    // --- Helper ---

    @SuppressWarnings("unchecked")
    private void stubRefreshResponse(Mono<Map<String, String>> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/internal/tenants/slug-map")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(response);
    }
}
