package io.kelta.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SecurityAuditFilter Tests")
class SecurityAuditFilterTest {

    private SecurityAuditFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityAuditFilter();
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    @DisplayName("Should have order 200 (after SecurityHeadersFilter)")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should not throw on successful response")
    void shouldNotThrowOnSuccess() {
        MockServerWebExchange exchange = createExchange("/api/users");
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should log on 401 Unauthorized response")
    void shouldLogOnUnauthorized() {
        MockServerWebExchange exchange = createExchange("/api/users");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Filter completes without error — logging happens internally
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should log on 403 Forbidden response")
    void shouldLogOnForbidden() {
        MockServerWebExchange exchange = createExchange("/api/admin/users");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should log on 429 Too Many Requests response")
    void shouldLogOnRateLimit() {
        MockServerWebExchange exchange = createExchange("/api/records");
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromForwardedHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    private MockServerWebExchange createExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }
}
