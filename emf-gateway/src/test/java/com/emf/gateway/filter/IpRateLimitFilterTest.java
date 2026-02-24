package com.emf.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IpRateLimitFilter.
 *
 * Tests verify that the filter:
 * - Only rate-limits unauthenticated endpoints
 * - Passes through authenticated/other endpoints without rate limiting
 * - Returns 429 when rate limit is exceeded
 * - Properly resolves client IPs from headers and remote address
 * - Cleans up stale entries
 */
@DisplayName("IpRateLimitFilter Tests")
class IpRateLimitFilterTest {

    private IpRateLimitFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new IpRateLimitFilter();
        filter.clearAll();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Path matching")
    class PathMatching {

        @Test
        @DisplayName("should rate-limit /actuator/health")
        void shouldRateLimitHealthPath() {
            assertThat(IpRateLimitFilter.isRateLimitedPath("/actuator/health")).isTrue();
        }

        @Test
        @DisplayName("should not rate-limit authenticated API paths")
        void shouldNotRateLimitApiPaths() {
            assertThat(IpRateLimitFilter.isRateLimitedPath("/api/collections")).isFalse();
        }

        @Test
        @DisplayName("should not rate-limit actuator metrics paths")
        void shouldNotRateLimitActuatorMetricsPaths() {
            assertThat(IpRateLimitFilter.isRateLimitedPath("/actuator/metrics")).isFalse();
        }

        @Test
        @DisplayName("should not rate-limit internal paths")
        void shouldNotRateLimitInternalPaths() {
            assertThat(IpRateLimitFilter.isRateLimitedPath("/internal/bootstrap")).isFalse();
        }
    }

    @Nested
    @DisplayName("Rate limiting behavior")
    class RateLimiting {

        @Test
        @DisplayName("should allow requests within limit")
        void shouldAllowRequestsWithinLimit() {
            MockServerWebExchange exchange = createExchange("/actuator/health", "192.168.1.1");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("should pass through non-rate-limited paths without counting")
        void shouldPassThroughNonRateLimitedPaths() {
            MockServerWebExchange exchange = createExchangeWithRemoteAddress("/api/collections");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any(ServerWebExchange.class));
            assertThat(filter.getTrackedIpCount()).isZero();
        }

        @Test
        @DisplayName("should return 429 when rate limit exceeded")
        void shouldReturn429WhenRateLimitExceeded() {
            String clientIp = "10.0.0.1";

            // Exhaust the rate limit
            for (int i = 0; i < IpRateLimitFilter.MAX_REQUESTS_PER_WINDOW; i++) {
                MockServerWebExchange exchange = createExchange("/actuator/health", clientIp);
                StepVerifier.create(filter.filter(exchange, chain))
                        .verifyComplete();
            }

            // Next request should be rate-limited
            MockServerWebExchange exceededExchange = createExchange("/actuator/health", clientIp);
            StepVerifier.create(filter.filter(exceededExchange, chain))
                    .verifyComplete();

            assertThat(exceededExchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exceededExchange.getResponse().getHeaders().getFirst("Retry-After"))
                    .isEqualTo("60");
        }

        @Test
        @DisplayName("should track IPs independently")
        void shouldTrackIpsIndependently() {
            // Fill up limit for IP 1
            for (int i = 0; i < IpRateLimitFilter.MAX_REQUESTS_PER_WINDOW; i++) {
                MockServerWebExchange exchange = createExchange("/actuator/health", "10.0.0.1");
                StepVerifier.create(filter.filter(exchange, chain))
                        .verifyComplete();
            }

            // IP 2 should still be allowed
            MockServerWebExchange otherIpExchange = createExchange("/actuator/health", "10.0.0.2");
            StepVerifier.create(filter.filter(otherIpExchange, chain))
                    .verifyComplete();

            // IP 2's request should have been passed to the chain (not blocked)
            assertThat(otherIpExchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("should return JSON error body on 429")
        void shouldReturnJsonErrorBodyOn429() {
            String clientIp = "10.0.0.50";

            // Exhaust the rate limit
            for (int i = 0; i < IpRateLimitFilter.MAX_REQUESTS_PER_WINDOW; i++) {
                MockServerWebExchange exchange = createExchange("/actuator/health", clientIp);
                StepVerifier.create(filter.filter(exchange, chain))
                        .verifyComplete();
            }

            MockServerWebExchange exceededExchange = createExchange("/actuator/health", clientIp);
            StepVerifier.create(filter.filter(exceededExchange, chain))
                    .verifyComplete();

            assertThat(exceededExchange.getResponse().getHeaders().getFirst("Content-Type"))
                    .isEqualTo("application/json");
        }
    }

    @Nested
    @DisplayName("Client IP resolution")
    class ClientIpResolution {

        @Test
        @DisplayName("should use X-Forwarded-For header when present")
        void shouldUseXForwardedForHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String ip = filter.resolveClientIp(exchange);
            assertThat(ip).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("should fall back to remote address when no X-Forwarded-For")
        void shouldFallBackToRemoteAddress() {
            MockServerWebExchange exchange = createExchangeWithRemoteAddress("/actuator/health");
            String ip = filter.resolveClientIp(exchange);
            // MockServerHttpRequest uses localhost by default
            assertThat(ip).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        @DisplayName("should clean up stale entries")
        void shouldCleanupStaleEntries() {
            // Add some entries
            MockServerWebExchange exchange = createExchange("/actuator/health", "10.0.0.99");
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(filter.getTrackedIpCount()).isEqualTo(1);

            // Cleanup should keep recent entries
            filter.cleanupStaleEntries();
            assertThat(filter.getTrackedIpCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("clearAll should remove all tracked IPs")
        void clearAllShouldRemoveAllTrackedIps() {
            MockServerWebExchange exchange1 = createExchange("/actuator/health", "10.0.0.1");
            MockServerWebExchange exchange2 = createExchange("/actuator/health", "10.0.0.2");

            StepVerifier.create(filter.filter(exchange1, chain)).verifyComplete();
            StepVerifier.create(filter.filter(exchange2, chain)).verifyComplete();

            assertThat(filter.getTrackedIpCount()).isEqualTo(2);

            filter.clearAll();
            assertThat(filter.getTrackedIpCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrdering {

        @Test
        @DisplayName("should have order -150 (before JWT filter)")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(-150);
        }
    }

    /**
     * Creates a MockServerWebExchange with an X-Forwarded-For header for the given IP.
     */
    private MockServerWebExchange createExchange(String path, String clientIp) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .header("X-Forwarded-For", clientIp)
                .build();
        return MockServerWebExchange.from(request);
    }

    /**
     * Creates a MockServerWebExchange using the default remote address (no X-Forwarded-For).
     */
    private MockServerWebExchange createExchangeWithRemoteAddress(String path) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
        return MockServerWebExchange.from(request);
    }
}
