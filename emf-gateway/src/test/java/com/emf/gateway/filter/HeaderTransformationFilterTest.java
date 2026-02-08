package com.emf.gateway.filter;

import com.emf.gateway.auth.GatewayPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HeaderTransformationFilter.
 *
 * Tests verify that:
 * - Authorization header is preserved for downstream JWT validation
 * - X-Forwarded-User header is added with principal username
 * - X-Forwarded-Roles header is added with comma-separated roles
 * - Other headers are preserved
 * - Filter handles unauthenticated requests gracefully
 */
class HeaderTransformationFilterTest {
    
    private HeaderTransformationFilter filter;
    private GatewayFilterChain chain;
    
    @BeforeEach
    void setUp() {
        filter = new HeaderTransformationFilter();
        chain = mock(GatewayFilterChain.class);
        
        // Mock chain to capture the mutated exchange
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange exchange = invocation.getArgument(0);
            return Mono.empty();
        });
    }
    
    @Test
    void shouldPreserveAuthorizationHeader() {
        // Given: A request with Authorization header and authenticated principal
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .header("X-Custom-Header", "custom-value")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayPrincipal principal = new GatewayPrincipal(
                "testuser",
                Arrays.asList("USER", "ADMIN"),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);

        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });

        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Then: Authorization header should be preserved for downstream JWT validation
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
    }
    
    @Test
    void shouldAddForwardedUserHeader() {
        // Given: A request with authenticated principal
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal(
                "john.doe",
                Arrays.asList("USER"),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: X-Forwarded-User header should be present
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-User")).isEqualTo("john.doe");
    }
    
    @Test
    void shouldAddForwardedRolesHeaderWithCommaSeparatedRoles() {
        // Given: A request with authenticated principal having multiple roles
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal(
                "admin.user",
                Arrays.asList("USER", "ADMIN", "MODERATOR"),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: X-Forwarded-Roles header should contain comma-separated roles
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-Roles")).isEqualTo("USER,ADMIN,MODERATOR");
    }
    
    @Test
    void shouldPreserveOtherHeaders() {
        // Given: A request with multiple headers
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .header("X-Custom-Header", "custom-value")
                .header("X-Request-ID", "12345")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal(
                "testuser",
                Arrays.asList("USER"),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: Other headers should be preserved (including Authorization)
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Custom-Header")).isEqualTo("custom-value");
        assertThat(headers.getFirst("X-Request-ID")).isEqualTo("12345");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
    }
    
    @Test
    void shouldHandleEmptyRolesList() {
        // Given: A request with principal having no roles
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal(
                "testuser",
                Collections.emptyList(),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: X-Forwarded-Roles header should be empty string
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-Roles")).isEqualTo("");
    }
    
    @Test
    void shouldSkipTransformationWhenNoPrincipal() {
        // Given: A request without authenticated principal (e.g., bootstrap endpoint)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/control/bootstrap")
                .header("X-Custom-Header", "custom-value")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No principal in attributes
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: Headers should remain unchanged
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.containsKey("X-Forwarded-User")).isFalse();
        assertThat(headers.containsKey("X-Forwarded-Roles")).isFalse();
        assertThat(headers.getFirst("X-Custom-Header")).isEqualTo("custom-value");
    }
    
    @Test
    void shouldHaveCorrectOrder() {
        // Then: Filter should run after authentication and authorization
        assertThat(filter.getOrder()).isEqualTo(50);
    }
    
    @Test
    void shouldHandleSingleRole() {
        // Given: A request with principal having single role
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayPrincipal principal = new GatewayPrincipal(
                "testuser",
                Arrays.asList("ADMIN"),
                Collections.emptyMap()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        
        // Capture the mutated exchange
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange[0] = invocation.getArgument(0);
            return Mono.empty();
        });
        
        // When: Filter is applied
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
        
        // Then: X-Forwarded-Roles header should contain single role without comma
        assertThat(capturedExchange[0]).isNotNull();
        HttpHeaders headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-Roles")).isEqualTo("ADMIN");
    }
}
