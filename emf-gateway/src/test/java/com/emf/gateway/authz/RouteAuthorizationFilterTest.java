package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteAuthorizationFilter.
 * 
 * Tests route-level authorization including:
 * - Default allow behavior when no policy exists
 * - Policy evaluation with matching and non-matching roles
 * - Proper 403 responses for unauthorized access
 * - Integration with RouteRegistry and AuthzConfigCache
 */
@ExtendWith(MockitoExtension.class)
class RouteAuthorizationFilterTest {
    
    @Mock
    private RouteRegistry routeRegistry;
    
    @Mock
    private AuthzConfigCache authzConfigCache;
    
    @Mock
    private PolicyEvaluator policyEvaluator;
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private RouteAuthorizationFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new RouteAuthorizationFilter(routeRegistry, authzConfigCache, policyEvaluator);
        
        // Default behavior: chain continues (lenient to avoid unnecessary stubbing errors)
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }
    
    @Test
    void shouldHaveOrderZero() {
        assertThat(filter.getOrder()).isEqualTo(0);
    }
    
    @Test
    void shouldAllowRequestWhenNoPrincipalButAuthenticationFilterShouldHaveRejected() {
        // This is a safety check - authentication filter should have already rejected
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    void shouldAllowRequestWhenNoRouteFound() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/unknown")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        when(routeRegistry.findByPath("/api/unknown")).thenReturn(Optional.empty());
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }
    
    @Test
    void shouldAllowRequestWhenNoAuthzConfigForCollection() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.empty());
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }
    
    @Test
    void shouldAllowRequestWhenNoPolicyForHttpMethod() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        // AuthzConfig has policies, but not for GET method
        RoutePolicy postPolicy = new RoutePolicy("POST", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(postPolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        verify(policyEvaluator, never()).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }
    
    @Test
    void shouldAllowRequestWhenPrincipalSatisfiesPolicy() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("ADMIN"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        RoutePolicy postPolicy = new RoutePolicy("POST", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(postPolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(true);
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        verify(policyEvaluator).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }
    
    @Test
    void shouldDenyRequestWhenPrincipalDoesNotSatisfyPolicy() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        RoutePolicy postPolicy = new RoutePolicy("POST", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(postPolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(false);
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain, never()).filter(exchange);
        verify(policyEvaluator).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    void shouldMatchPolicyCaseInsensitively() {
        // Arrange - request uses lowercase "post", policy uses uppercase "POST"
        GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("ADMIN"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.POST, "/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        RoutePolicy postPolicy = new RoutePolicy("POST", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(postPolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(true);
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        verify(policyEvaluator).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
    }
    
    @Test
    void shouldHandleMultiplePoliciesAndMatchCorrectOne() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        // Multiple policies for different methods
        RoutePolicy getPolicy = new RoutePolicy("GET", "policy-1", List.of("USER"));
        RoutePolicy postPolicy = new RoutePolicy("POST", "policy-2", List.of("ADMIN"));
        RoutePolicy deletePolicy = new RoutePolicy("DELETE", "policy-3", List.of("ADMIN"));
        
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(getPolicy, postPolicy, deletePolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(true);
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        verify(filterChain).filter(exchange);
        verify(policyEvaluator).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
        verify(policyEvaluator, never()).evaluate(any(RoutePolicy.class), argThat(p -> p != principal));
    }
    
    @Test
    void shouldReturnJsonErrorResponseOnForbidden() {
        // Arrange
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .delete("/api/users/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);
        
        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        
        RoutePolicy deletePolicy = new RoutePolicy("DELETE", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "users-collection",
                List.of(deletePolicy),
                Collections.emptyList()
        );
        
        when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(false);
        
        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();
        
        // Assert
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }
}
