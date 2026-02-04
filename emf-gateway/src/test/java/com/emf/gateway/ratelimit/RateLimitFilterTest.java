package com.emf.gateway.ratelimit;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.route.RateLimitConfig;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {
    
    @Mock
    private RouteRegistry routeRegistry;
    
    @Mock
    private RedisRateLimiter rateLimiter;
    
    @Mock
    private GatewayFilterChain chain;
    
    private RateLimitFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(routeRegistry, rateLimiter);
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
        verify(routeRegistry, never()).findByPath(anyString());
        verify(rateLimiter, never()).checkRateLimit(anyString(), anyString(), any());
    }
    
    @Test
    void testNoRouteFound_SkipsRateLimiting() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.empty());
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
        
        verify(chain).filter(exchange);
        verify(rateLimiter, never()).checkRateLimit(anyString(), anyString(), any());
    }
    
    @Test
    void testNoRateLimitConfigured_SkipsRateLimiting() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
            "users-collection", "user-service", "/api/users/**",
            "http://user-service:8080", "users"
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        
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
        
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        RouteDefinition route = new RouteDefinition(
            "users-collection", "user-service", "/api/users/**",
            "http://user-service:8080", "users", config
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .thenReturn(Mono.just(RateLimitResult.allowed(5)));
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
        
        verify(chain).filter(exchange);
        
        // Verify rate limit headers are added
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("10", headers.getFirst("X-RateLimit-Limit"));
        assertEquals("5", headers.getFirst("X-RateLimit-Remaining"));
        assertNotNull(headers.getFirst("X-RateLimit-Reset"));
    }
    
    @Test
    void testRateLimitExceeded_Returns429() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal("user@example.com", List.of("USER"), null);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        RouteDefinition route = new RouteDefinition(
            "users-collection", "user-service", "/api/users/**",
            "http://user-service:8080", "users", config
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .thenReturn(Mono.just(RateLimitResult.notAllowed(Duration.ofSeconds(60))));
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
        
        verify(chain, never()).filter(exchange);
        
        // Verify response status
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        
        // Verify rate limit headers
        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("10", headers.getFirst("X-RateLimit-Limit"));
        assertEquals("0", headers.getFirst("X-RateLimit-Remaining"));
        assertEquals("60", headers.getFirst("Retry-After"));
        assertNotNull(headers.getFirst("X-RateLimit-Reset"));
        assertEquals("application/json", headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }
    
    @Test
    void testDifferentPrincipalsHaveSeparateLimits() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        RouteDefinition route = new RouteDefinition(
            "users-collection", "user-service", "/api/users/**",
            "http://user-service:8080", "users", config
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        
        // First principal
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        GatewayPrincipal principal1 = new GatewayPrincipal("user1@example.com", List.of("USER"), null);
        exchange1.getAttributes().put("gateway.principal", principal1);
        
        when(rateLimiter.checkRateLimit("users-collection", "user1@example.com", config))
            .thenReturn(Mono.just(RateLimitResult.allowed(9)));
        
        // Second principal
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/users").build();
        ServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        GatewayPrincipal principal2 = new GatewayPrincipal("user2@example.com", List.of("USER"), null);
        exchange2.getAttributes().put("gateway.principal", principal2);
        
        when(rateLimiter.checkRateLimit("users-collection", "user2@example.com", config))
            .thenReturn(Mono.just(RateLimitResult.allowed(9)));
        
        // When & Then
        StepVerifier.create(filter.filter(exchange1, chain))
            .verifyComplete();
        
        StepVerifier.create(filter.filter(exchange2, chain))
            .verifyComplete();
        
        verify(rateLimiter).checkRateLimit("users-collection", "user1@example.com", config);
        verify(rateLimiter).checkRateLimit("users-collection", "user2@example.com", config);
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
        
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        RouteDefinition route = new RouteDefinition(
            "users-collection", "user-service", "/api/users/**",
            "http://user-service:8080", "users", config
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
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
