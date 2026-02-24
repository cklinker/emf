package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.filter.TenantResolutionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PermissionResolutionFilter}.
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolutionFilterTest {

    /** Matches the private constant in JwtAuthenticationFilter. */
    private static final String PRINCIPAL_ATTR = "gateway.principal";

    @Mock
    private PermissionResolutionService permissionService;

    @Mock
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should have order -5")
    void shouldHaveCorrectOrder() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, true);
        assertThat(filter.getOrder()).isEqualTo(-5);
    }

    @Test
    @DisplayName("Should pass through when permissions disabled")
    void shouldPassThroughWhenDisabled() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, false);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(permissionService, never()).resolvePermissions(any(), any());
        assertThat(PermissionResolutionFilter.getPermissions(exchange)).isNull();
    }

    @Test
    @DisplayName("Should pass through when no principal")
    void shouldPassThroughWhenNoPrincipal() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, true);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(permissionService, never()).resolvePermissions(any(), any());
    }

    @Test
    @DisplayName("Should set allPermissive for PLATFORM_ADMIN")
    void shouldSetAllPermissiveForPlatformAdmin() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, true);

        GatewayPrincipal admin = new GatewayPrincipal("admin@test.com",
                List.of("PLATFORM_ADMIN"), Map.of());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());
        exchange.getAttributes().put(PRINCIPAL_ATTR, admin);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(permissionService, never()).resolvePermissions(any(), any());
        ResolvedPermissions perms = PermissionResolutionFilter.getPermissions(exchange);
        assertThat(perms).isNotNull();
        assertThat(perms.isAllPermissive()).isTrue();
    }

    @Test
    @DisplayName("Should pass through when no tenant context")
    void shouldPassThroughWhenNoTenantContext() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, true);

        GatewayPrincipal user = new GatewayPrincipal("user@test.com",
                List.of("USER"), Map.of());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());
        exchange.getAttributes().put(PRINCIPAL_ATTR, user);
        // No tenant ID set

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(permissionService, never()).resolvePermissions(any(), any());
    }

    @Test
    @DisplayName("Should resolve permissions for normal user")
    void shouldResolvePermissionsForNormalUser() {
        PermissionResolutionFilter filter = new PermissionResolutionFilter(permissionService, true);

        GatewayPrincipal user = new GatewayPrincipal("user@test.com",
                List.of("USER"), Map.of());

        ResolvedPermissions perms = new ResolvedPermissions(
                "user-123",
                Map.of("API_ACCESS", true),
                Map.of("coll-1", new ObjectPermissions(true, true, false, false, false, false)),
                Collections.emptyMap()
        );

        when(permissionService.resolvePermissions("tenant-1", "user@test.com"))
                .thenReturn(Mono.just(perms));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());
        exchange.getAttributes().put(PRINCIPAL_ATTR, user);
        exchange.getAttributes().put(TenantResolutionFilter.TENANT_ID_ATTR, "tenant-1");

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        ResolvedPermissions resolved = PermissionResolutionFilter.getPermissions(exchange);
        assertThat(resolved).isNotNull();
        assertThat(resolved.userId()).isEqualTo("user-123");
        assertThat(resolved.hasSystemPermission("API_ACCESS")).isTrue();
    }
}
