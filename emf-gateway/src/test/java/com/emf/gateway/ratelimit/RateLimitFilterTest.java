package com.emf.gateway.ratelimit;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.route.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter.
 * Tests per-tenant rate limiting based on governor limits.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RedisRateLimiter rateLimiter;

    @Mock
    private GatewayFilterChain chain;

    private TenantGovernorLimitCache governorLimitCache;
    private RateLimitFilter filter;

    private static final String TENANT_ID = "test-tenant-id";

    @BeforeEach
    void setUp() {
        governorLimitCache = new TenantGovernorLimitCache();
        filter = new RateLimitFilter(rateLimiter, governorLimitCache);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testNoPrincipal_SkipsRateLimiting() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);
        verify(rateLimiter, never()).checkRateLimit(anyString(), anyString(), any());
    }

    @Test
    void testNoTenantContext_SkipsRateLimiting() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        // No tenant ID set

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);
        verify(rateLimiter, never()).checkRateLimit(anyString(), anyString(), any());
    }

    @Test
    void testRateLimitAllowed_AddsHeadersAndContinues() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        exchange.getAttributes().put("tenantId", TENANT_ID);

        // Governor limits: 100,000 per day → ~69 per minute
        governorLimitCache.updateTenantLimit(TENANT_ID, 100_000);
        RateLimitConfig expectedConfig = governorLimitCache.getRateLimitForTenant(TENANT_ID);

        when(rateLimiter.checkRateLimit(eq(TENANT_ID), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.allowed(50)));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);

        // Verify rate limit headers are added
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals(String.valueOf(expectedConfig.getRequestsPerWindow()), headers.getFirst("X-RateLimit-Limit"));
        assertEquals("50", headers.getFirst("X-RateLimit-Remaining"));
        assertNotNull(headers.getFirst("X-RateLimit-Reset"));
    }

    @Test
    void testRateLimitExceeded_Returns429() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        exchange.getAttributes().put("tenantId", TENANT_ID);

        governorLimitCache.updateTenantLimit(TENANT_ID, 100_000);

        when(rateLimiter.checkRateLimit(eq(TENANT_ID), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.notAllowed(Duration.ofSeconds(60))));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain, never()).filter(exchange);

        // Verify response status
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());

        // Verify rate limit headers
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertNotNull(headers.getFirst("X-RateLimit-Limit"));
        assertEquals("0", headers.getFirst("X-RateLimit-Remaining"));
        assertEquals("60", headers.getFirst("Retry-After"));
        assertNotNull(headers.getFirst("X-RateLimit-Reset"));
        assertEquals("application/json", headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void testDifferentTenants_HaveSeparateLimits() {
        // Given
        String tenantId1 = "tenant-1";
        String tenantId2 = "tenant-2";

        governorLimitCache.updateTenantLimit(tenantId1, 50_000);
        governorLimitCache.updateTenantLimit(tenantId2, 200_000);

        // First tenant
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        GatewayPrincipal principal1 = new GatewayPrincipal("user1@example.com", List.of("USER"), null);
        exchange1.getAttributes().put("gateway.principal", principal1);
        exchange1.getAttributes().put("tenantId", tenantId1);

        when(rateLimiter.checkRateLimit(eq(tenantId1), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.allowed(9)));

        // Second tenant
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        GatewayPrincipal principal2 = new GatewayPrincipal("user2@example.com", List.of("USER"), null);
        exchange2.getAttributes().put("gateway.principal", principal2);
        exchange2.getAttributes().put("tenantId", tenantId2);

        when(rateLimiter.checkRateLimit(eq(tenantId2), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.allowed(9)));

        // When & Then
        StepVerifier.create(filter.filter(exchange1, chain))
            .verifyComplete();

        StepVerifier.create(filter.filter(exchange2, chain))
            .verifyComplete();

        verify(rateLimiter).checkRateLimit(eq(tenantId1), eq("tenant"), any(RateLimitConfig.class));
        verify(rateLimiter).checkRateLimit(eq(tenantId2), eq("tenant"), any(RateLimitConfig.class));
    }

    @Test
    void testDefaultLimitsUsedForUnknownTenant() {
        // Given - tenant not in cache, should use default (100,000/day)
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        exchange.getAttributes().put("tenantId", "unknown-tenant");

        when(rateLimiter.checkRateLimit(eq("unknown-tenant"), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.allowed(50)));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);
        verify(rateLimiter).checkRateLimit(eq("unknown-tenant"), eq("tenant"), any(RateLimitConfig.class));
    }

    @Test
    void testFilterOrder() {
        // Filter should run after authentication (-100) but before routing (0)
        assertEquals(-50, filter.getOrder());
    }

    @Test
    void testRateLimitWithZeroRemaining() {
        // Given - last allowed request
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        exchange.getAttributes().put("tenantId", TENANT_ID);

        governorLimitCache.updateTenantLimit(TENANT_ID, 100_000);

        when(rateLimiter.checkRateLimit(eq(TENANT_ID), eq("tenant"), any(RateLimitConfig.class)))
            .thenReturn(Mono.just(RateLimitResult.allowed(0)));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);

        // Verify headers show 0 remaining but request is still allowed
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("0", headers.getFirst("X-RateLimit-Remaining"));
    }
}
