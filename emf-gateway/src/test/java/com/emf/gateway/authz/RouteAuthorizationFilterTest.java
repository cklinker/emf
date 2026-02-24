package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteAuthorizationFilter.
 *
 * Tests authentication-only enforcement:
 * - Bootstrap endpoint bypass (no auth required)
 * - Authenticated users pass through
 * - Unauthenticated users get 403
 */
@ExtendWith(MockitoExtension.class)
class RouteAuthorizationFilterTest {

    @Mock
    private GatewayFilterChain filterChain;

    private RouteAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RouteAuthorizationFilter();

        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void shouldHaveOrderZero() {
        assertThat(filter.getOrder()).isEqualTo(0);
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
    void shouldAllowAuthenticatedUsers() {
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error set
    }

    @Test
    void shouldAllowAuthenticatedUsersToAnyApiPath() {
        GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("ADMIN"), Map.of());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("gateway.principal", principal);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldReturnJsonErrorResponseOnForbidden() {
        MockServerHttpRequest request = MockServerHttpRequest
                .delete("/api/users/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .expectComplete()
                .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }
}
