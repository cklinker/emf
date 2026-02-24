package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.PublicPathMatcher;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RouteAuthorizationFilter}.
 *
 * <p>Tests both authentication-only mode (permissions disabled) and
 * full object-permission enforcement mode (permissions enabled).
 */
@ExtendWith(MockitoExtension.class)
class RouteAuthorizationFilterTest {

    /** Matches the private constant in JwtAuthenticationFilter. */
    private static final String PRINCIPAL_ATTR = "gateway.principal";

    @Mock
    private RouteRegistry routeRegistry;

    @Mock
    private PublicPathMatcher publicPathMatcher;

    @Mock
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        lenient().when(publicPathMatcher.isPublicRequest(any(ServerWebExchange.class))).thenReturn(false);
    }

    @Test
    void shouldHaveOrderZero() {
        RouteAuthorizationFilter filter = new RouteAuthorizationFilter(routeRegistry, false, publicPathMatcher);
        assertThat(filter.getOrder()).isEqualTo(0);
    }

    // ================================================================
    // Permissions disabled (authentication-only mode)
    // ================================================================

    @Nested
    @DisplayName("When permissions disabled")
    class PermissionsDisabledTests {

        private RouteAuthorizationFilter filter;

        @BeforeEach
        void setUp() {
            filter = new RouteAuthorizationFilter(routeRegistry, false, publicPathMatcher);
        }

        @Test
        @DisplayName("Should allow public path without principal")
        void shouldAllowPublicPathWithoutPrincipal() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/ui-pages").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(publicPathMatcher.isPublicRequest(exchange)).thenReturn(true);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Should return forbidden when no principal")
        void shouldReturnForbiddenWhenNoPrincipal() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(filterChain, never()).filter(exchange);
        }

        @Test
        @DisplayName("Should allow authenticated users")
        void shouldAllowAuthenticatedUsers() {
            GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Should allow authenticated users to any API path")
        void shouldAllowAuthenticatedUsersToAnyApiPath() {
            GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("ADMIN"), Map.of());
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/collections").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should return JSON error response on forbidden")
        void shouldReturnJsonErrorResponseOnForbidden() {
            MockServerHttpRequest request = MockServerHttpRequest.delete("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                    .contains("application/json");
        }
    }

    // ================================================================
    // Permissions enabled (object-level enforcement)
    // ================================================================

    @Nested
    @DisplayName("When permissions enabled")
    class PermissionsEnabledTests {

        private RouteAuthorizationFilter filter;

        @BeforeEach
        void setUp() {
            filter = new RouteAuthorizationFilter(routeRegistry, true, publicPathMatcher);
        }

        @Test
        @DisplayName("Should allow public path without principal")
        void shouldAllowPublicPathWithoutPrincipal() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/oidc-providers").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(publicPathMatcher.isPublicRequest(exchange)).thenReturn(true);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Should return forbidden when no principal")
        void shouldReturnForbiddenWhenNoPrincipal() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow allPermissive permissions (platform admin)")
        void shouldAllowAllPermissive() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("admin@test.com", List.of("PLATFORM_ADMIN"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE,
                    ResolvedPermissions.allPermissive());

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should fail-open when no permissions attribute")
        void shouldFailOpenWhenNoPermissions() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny when missing API_ACCESS system permission")
        void shouldDenyWithoutApiAccess() {
            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", false),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow non-API paths even without permissions")
        void shouldAllowNonApiPaths() {
            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should allow GET when user has canRead permission")
        void shouldAllowGetWithReadPermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(false, true, false, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny GET when user lacks canRead permission")
        void shouldDenyGetWithoutReadPermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(true, false, true, true, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow POST when user has canCreate permission")
        void shouldAllowPostWithCreatePermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(true, true, false, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny POST when user lacks canCreate permission")
        void shouldDenyPostWithoutCreatePermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(false, true, true, true, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow PUT when user has canEdit permission")
        void shouldAllowPutWithEditPermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(false, true, true, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.put("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should allow PATCH when user has canEdit permission")
        void shouldAllowPatchWithEditPermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(false, true, true, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.patch("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should allow DELETE when user has canDelete permission")
        void shouldAllowDeleteWithDeletePermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(false, true, false, true, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.delete("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny DELETE when user lacks canDelete permission")
        void shouldDenyDeleteWithoutDeletePermission() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Map.of("coll-1", new ObjectPermissions(true, true, true, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.delete("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow GET with VIEW_ALL_DATA system permission override")
        void shouldAllowGetWithViewAllDataOverride() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true, "VIEW_ALL_DATA", true),
                    Map.of("coll-1", new ObjectPermissions(false, false, false, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should allow POST with MODIFY_ALL_DATA system permission override")
        void shouldAllowPostWithModifyAllDataOverride() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true, "MODIFY_ALL_DATA", true),
                    Map.of("coll-1", new ObjectPermissions(false, false, false, false, false, false)),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should allow through when no matching route found")
        void shouldAllowThroughWhenNoRouteFound() {
            when(routeRegistry.findByPath("/api/unknown")).thenReturn(Optional.empty());

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/unknown").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny when collection has no object permissions configured")
        void shouldDenyWhenNoObjectPermissionsForCollection() {
            RouteDefinition route = new RouteDefinition("coll-99", "/api/products/**",
                    "http://worker:80", "products");
            when(routeRegistry.findByPath("/api/products")).thenReturn(Optional.of(route));

            ResolvedPermissions perms = new ResolvedPermissions(
                    "user-1",
                    Map.of("API_ACCESS", true),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/products").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR,
                    new GatewayPrincipal("user@test.com", List.of("USER"), Map.of()));
            exchange.getAttributes().put(PermissionResolutionFilter.PERMISSIONS_ATTRIBUTE, perms);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
