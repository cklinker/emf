package com.emf.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SecurityHeadersFilter.
 *
 * Tests verify that the filter adds all required security headers
 * to every gateway response following OWASP best practices.
 */
@DisplayName("SecurityHeadersFilter Tests")
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    @DisplayName("Should add X-Content-Type-Options header")
    void shouldAddXContentTypeOptionsHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("Should add X-Frame-Options header")
    void shouldAddXFrameOptionsHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("Should add Strict-Transport-Security header")
    void shouldAddStrictTransportSecurityHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    @DisplayName("Should add Referrer-Policy header")
    void shouldAddReferrerPolicyHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("Should add Permissions-Policy header")
    void shouldAddPermissionsPolicyHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
    }

    @Test
    @DisplayName("Should add Cache-Control header")
    void shouldAddCacheControlHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("Should add Pragma header")
    void shouldAddPragmaHeader() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("Should add all security headers to every response")
    void shouldAddAllSecurityHeaders() {
        MockServerWebExchange exchange = createExchange("/api/data");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(headers.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("Should have correct order (100)")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should add headers for different paths")
    void shouldAddHeadersForDifferentPaths() {
        // Test actuator path
        MockServerWebExchange actuatorExchange = createExchange("/actuator/health");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(actuatorExchange, chain))
                .verifyComplete();

        assertThat(actuatorExchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");

        // Test control path
        MockServerWebExchange controlExchange = createExchange("/control/bootstrap");
        StepVerifier.create(filter.filter(controlExchange, chain))
                .verifyComplete();

        assertThat(controlExchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    private MockServerWebExchange createExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
        return MockServerWebExchange.from(request);
    }
}
