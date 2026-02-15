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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteAuthorizationFilter.
 *
 * Tests route-level authorization using profile-based permissions including:
 * - Bootstrap endpoint bypass
 * - ProfilePolicyEvaluator delegation
 * - Proper 403 responses with JSON error body
 * - Integration with RouteRegistry
 */
@ExtendWith(MockitoExtension.class)
class RouteAuthorizationFilterTest {

    @Mock
    private RouteRegistry routeRegistry;

    @Mock
    private ProfilePolicyEvaluator profilePolicyEvaluator;

    @Mock
    private GatewayFilterChain filterChain;

    private RouteAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RouteAuthorizationFilter(routeRegistry, profilePolicyEvaluator);

        // Default behavior: chain continues (lenient to avoid unnecessary stubbing errors)
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void shouldHaveOrderZero() {
        assertThat(filter.getOrder()).isEqualTo(0);
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
    void shouldReturnForbiddenWhenNoPrincipal() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(filterChain, never()).filter(exchange);
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
    void shouldAllowWhenProfileEvaluatorAllows() {
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

        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET)))
                .thenReturn(Mono.just(true));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert
        verify(profilePolicyEvaluator).evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET));
        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }

    @Test
    void shouldDenyWhenProfileEvaluatorDenies() {
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

        when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET)))
                .thenReturn(Mono.just(false));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert
        verify(profilePolicyEvaluator).evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.GET));
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

        when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), eq("users-collection"), eq(HttpMethod.DELETE)))
                .thenReturn(Mono.just(false));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }

    @Test
    void shouldEvaluateControlPlaneRoutes() {
        // Arrange - control-plane routes go through normal profile evaluation
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

        when(routeRegistry.findByPath("/control/collections")).thenReturn(Optional.of(route));
        when(profilePolicyEvaluator.evaluate(any(GatewayPrincipal.class), eq("00000000-0000-0000-0000-000000000100"), eq(HttpMethod.GET)))
                .thenReturn(Mono.just(true));

        // Act
        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        // Assert - should go through normal profile evaluation
        verify(profilePolicyEvaluator).evaluate(any(GatewayPrincipal.class), eq("00000000-0000-0000-0000-000000000100"), eq(HttpMethod.GET));
        verify(filterChain).filter(exchange);
    }
}
