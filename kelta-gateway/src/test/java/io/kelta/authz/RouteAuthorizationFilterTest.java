package io.kelta.gateway.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.auth.PublicPathMatcher;
import io.kelta.gateway.authz.cerbos.CerbosAuthorizationService;
import io.kelta.gateway.metrics.GatewayMetrics;
import io.kelta.gateway.route.RouteDefinition;
import io.kelta.gateway.route.RouteRegistry;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RouteAuthorizationFilter}.
 *
 * <p>Tests both authentication-only mode (permissions disabled) and
 * full Cerbos-based authorization mode (permissions enabled).
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
    private GatewayMetrics metrics;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private CerbosAuthorizationService cerbosService;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        lenient().when(publicPathMatcher.isPublicRequest(any(ServerWebExchange.class))).thenReturn(false);
    }

    private GatewayPrincipal principalWithIdentity(String email) {
        GatewayPrincipal principal = new GatewayPrincipal(email, List.of("USER"), Map.of());
        principal.setProfileId("profile-1");
        principal.setProfileName("Standard User");
        principal.setTenantId("tenant-1");
        return principal;
    }

    @Test
    void shouldHaveOrderZero() {
        RouteAuthorizationFilter filter = new RouteAuthorizationFilter(
                routeRegistry, false, publicPathMatcher, metrics, new ObjectMapper(), cerbosService);
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
            filter = new RouteAuthorizationFilter(
                    routeRegistry, false, publicPathMatcher, metrics, new ObjectMapper(), cerbosService);
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
        @DisplayName("Should allow authenticated users without Cerbos check")
        void shouldAllowAuthenticatedUsersWithoutCerbosCheck() {
            GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
            verifyNoInteractions(cerbosService);
        }
    }

    // ================================================================
    // Permissions enabled (Cerbos-based enforcement)
    // ================================================================

    @Nested
    @DisplayName("When permissions enabled")
    class PermissionsEnabledTests {

        private RouteAuthorizationFilter filter;

        @BeforeEach
        void setUp() {
            filter = new RouteAuthorizationFilter(
                    routeRegistry, true, publicPathMatcher, metrics, new ObjectMapper(), cerbosService);
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
        @DisplayName("Should deny when principal has no profileId resolved")
        void shouldDenyWhenNoProfileResolved() {
            GatewayPrincipal principal = new GatewayPrincipal("user@test.com", List.of("USER"), Map.of());
            // No profileId or tenantId set
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(cerbosService);
        }

        @Test
        @DisplayName("Should deny when missing API_ACCESS system permission")
        void shouldDenyWithoutApiAccess() {
            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow non-API paths with identity")
        void shouldAllowNonApiPaths() {
            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
            verifyNoInteractions(cerbosService);
        }

        @Test
        @DisplayName("Should allow GET when Cerbos grants read")
        void shouldAllowGetWithCerbosRead() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "read"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
        }

        @Test
        @DisplayName("Should deny GET when Cerbos denies read")
        void shouldDenyGetWhenCerbosDeniesRead() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "read"))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow POST when Cerbos grants create")
        void shouldAllowPostWithCerbosCreate() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "create"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
        }

        @Test
        @DisplayName("Should deny POST when Cerbos denies create")
        void shouldDenyPostWhenCerbosDeniesCreate() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "create"))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow PUT when Cerbos grants edit")
        void shouldAllowPutWithCerbosEdit() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.put("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "edit"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
        }

        @Test
        @DisplayName("Should allow DELETE when Cerbos grants delete")
        void shouldAllowDeleteWithCerbosDelete() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.delete("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "delete"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
        }

        @Test
        @DisplayName("Should deny DELETE when Cerbos denies delete")
        void shouldDenyDeleteWhenCerbosDeniesDelete() {
            RouteDefinition route = new RouteDefinition("coll-1", "/api/users/**",
                    "http://worker:80", "users");
            when(routeRegistry.findByPath("/api/users/123")).thenReturn(Optional.of(route));

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.delete("/api/users/123").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));
            when(cerbosService.checkObjectPermission(principal, "coll-1", "delete"))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should allow through when no matching route found")
        void shouldAllowThroughWhenNoRouteFound() {
            when(routeRegistry.findByPath("/api/unknown")).thenReturn(Optional.empty());

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/unknown").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            verify(filterChain).filter(any());
        }

        @Test
        @DisplayName("Should forward identity headers to worker")
        void shouldForwardIdentityHeaders() {
            when(routeRegistry.findByPath("/api/users")).thenReturn(Optional.empty());

            GatewayPrincipal principal = principalWithIdentity("user@test.com");
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put(PRINCIPAL_ATTR, principal);

            when(cerbosService.checkSystemPermission(principal, "API_ACCESS"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(filter.filter(exchange, filterChain))
                    .expectComplete()
                    .verify();

            // Verify the chain was called with mutated exchange containing headers
            verify(filterChain).filter(argThat(ex -> {
                ServerWebExchange mutated = (ServerWebExchange) ex;
                return "user@test.com".equals(mutated.getRequest().getHeaders().getFirst("X-User-Email"))
                        && "profile-1".equals(mutated.getRequest().getHeaders().getFirst("X-User-Profile-Id"))
                        && "tenant-1".equals(mutated.getRequest().getHeaders().getFirst("X-Cerbos-Scope"));
            }));
        }
    }
}
