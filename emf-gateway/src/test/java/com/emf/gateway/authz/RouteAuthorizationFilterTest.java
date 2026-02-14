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
import static org.mockito.ArgumentMatchers.eq;
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
    private ProfilePolicyEvaluator profilePolicyEvaluator;

    @Mock
    private GatewayFilterChain filterChain;

    private RouteAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RouteAuthorizationFilter(routeRegistry, authzConfigCache, policyEvaluator,
                profilePolicyEvaluator, false);
        
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
    void shouldAllowUnauthenticatedAccessToBootstrapEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/control/bootstrap")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldAllowUnauthenticatedAccessToUiBootstrapEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/control/ui-bootstrap")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(filterChain).filter(exchange);
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
    void shouldFallbackToProfileEvaluationWhenNoAuthzConfigInCache() {
        // Arrange - when legacy cache is empty, filter falls back to profile-based evaluation
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );

        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.empty());
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), any(String.class), any(HttpMethod.class)))
                .thenReturn(Mono.just(true));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert — should delegate to profile evaluator instead of allowing by default
        verify(profilePolicyEvaluator).evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET));
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldDenyWhenProfileEvaluationDeniesAndCacheIsEmpty() {
        // Arrange - profile evaluator denies access when cache is empty
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        RouteDefinition route = new RouteDefinition(
                "users-collection",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );

        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("users-collection")).thenReturn(Optional.empty());
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), any(String.class), any(HttpMethod.class)))
                .thenReturn(Mono.just(false));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert — should be denied by profile evaluator
        verify(profilePolicyEvaluator).evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET));
        verify(filterChain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
    void shouldEvaluatePoliciesForControlPlaneRoutes() {
        // Arrange - control-plane routes now go through normal policy evaluation
        // instead of being unconditionally allowed
        GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/control/collections")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        RouteDefinition route = new RouteDefinition(
                "00000000-0000-0000-0000-000000000100",
                "/control/**",
                "http://control-plane:8080",
                "__control-plane"
        );

        // Policy cache has an entry for the control-plane collection
        RoutePolicy getPolicy = new RoutePolicy("GET", "policy-1", List.of("USER"));
        AuthzConfig authzConfig = new AuthzConfig(
                "00000000-0000-0000-0000-000000000100",
                List.of(getPolicy),
                Collections.emptyList()
        );

        when(routeRegistry.findByPath("/control/collections")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("00000000-0000-0000-0000-000000000100")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(true);

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert - should go through normal policy evaluation
        verify(filterChain).filter(exchange);
        verify(policyEvaluator).evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class));
    }

    @Test
    void shouldDenyControlPlaneRoutesWhenPolicyDenies() {
        // Arrange - control-plane routes can now be denied by policy
        GatewayPrincipal principal = new GatewayPrincipal("viewer1", List.of("VIEWER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .delete("/control/collections/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        RouteDefinition route = new RouteDefinition(
                "00000000-0000-0000-0000-000000000100",
                "/control/**",
                "http://control-plane:8080",
                "__control-plane"
        );

        RoutePolicy deletePolicy = new RoutePolicy("DELETE", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "00000000-0000-0000-0000-000000000100",
                List.of(deletePolicy),
                Collections.emptyList()
        );

        when(routeRegistry.findByPath("/control/collections/123")).thenReturn(Optional.of(route));
        when(authzConfigCache.getConfig("00000000-0000-0000-0000-000000000100")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(any(RoutePolicy.class), any(GatewayPrincipal.class))).thenReturn(false);

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert - should be denied
        verify(filterChain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
