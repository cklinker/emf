package io.kelta.gateway.filter;

import io.kelta.gateway.geo.ClientIpResolver;
import io.kelta.gateway.ratelimit.RateLimitExemptionService;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IpRateLimitFilter.
 *
 * Tests verify that the filter:
 * - Rate-limits only the configured public path prefixes (longest-prefix match)
 * - Gives each path prefix its own per-IP bucket
 * - Honours the CIDR exemption list
 * - Returns 429 with an honest Retry-After when a budget is exhausted
 * - Resolves client IPs from headers and remote address, and cleans up stale entries
 */
@DisplayName("IpRateLimitFilter Tests")
class IpRateLimitFilterTest {

    /** Small budgets keep the exhaustion loops fast. */
    private static final List<String> PATHS =
            List.of("/actuator/health=3", "/api/billing/webhooks=5");

    private IpRateLimitFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = newFilter(List.of());
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private IpRateLimitFilter newFilter(List<String> exemptCidrs) {
        ClientIpResolver resolver = new ClientIpResolver(true);
        IpRateLimitFilter f = new IpRateLimitFilter(
                resolver, new RateLimitExemptionService(resolver, exemptCidrs), PATHS);
        f.clearAll();
        return f;
    }

    @Nested
    @DisplayName("Path matching")
    class PathMatching {

        @Test
        @DisplayName("should rate-limit /actuator/health")
        void shouldRateLimitHealthPath() {
            assertThat(filter.matchPath("/actuator/health")).isEqualTo("/actuator/health");
        }

        @Test
        @DisplayName("should match a configured prefix against a deeper sub-path")
        void shouldMatchPrefixAgainstSubPath() {
            // The regression this guards: exact Set membership would miss the real
            // webhook URL, silently leaving it unlimited.
            assertThat(filter.matchPath("/api/billing/webhooks/stripe/tenant-123"))
                    .isEqualTo("/api/billing/webhooks");
        }

        @Test
        @DisplayName("should not rate-limit authenticated API paths")
        void shouldNotRateLimitApiPaths() {
            assertThat(filter.matchPath("/api/collections")).isNull();
        }

        @Test
        @DisplayName("should not rate-limit actuator metrics paths")
        void shouldNotRateLimitActuatorMetricsPaths() {
            assertThat(filter.matchPath("/actuator/metrics")).isNull();
        }

        @Test
        @DisplayName("should not rate-limit internal paths")
        void shouldNotRateLimitInternalPaths() {
            assertThat(filter.matchPath("/internal/bootstrap")).isNull();
        }

        @Test
        @DisplayName("should prefer the longest matching prefix")
        void shouldPreferLongestPrefix() {
            Map<String, Integer> budgets = IpRateLimitFilter.parsePathBudgets(
                    List.of("/api/billing=10", "/api/billing/webhooks=300"));
            IpRateLimitFilter f = new IpRateLimitFilter(
                    new ClientIpResolver(true),
                    new RateLimitExemptionService(new ClientIpResolver(true), List.of()),
                    List.of("/api/billing=10", "/api/billing/webhooks=300"));
            assertThat(budgets).containsEntry("/api/billing/webhooks", 300);
            assertThat(f.matchPath("/api/billing/webhooks/stripe/t1"))
                    .isEqualTo("/api/billing/webhooks");
            assertThat(f.matchPath("/api/billing/plans")).isEqualTo("/api/billing");
        }

        @Test
        @DisplayName("should skip malformed budget entries rather than fail")
        void shouldSkipMalformedEntries() {
            Map<String, Integer> budgets = IpRateLimitFilter.parsePathBudgets(
                    List.of("/good=5", "=10", "/bad=zero", "/nonpositive=0", "  "));
            assertThat(budgets).containsEntry("/good", 5);
            assertThat(budgets).containsKey("/bad"); // falls back to the default budget
            assertThat(budgets).doesNotContainKey("/nonpositive");
            assertThat(budgets).doesNotContainKey("");
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
            exhaust("/actuator/health", clientIp, 3);

            MockServerWebExchange exceededExchange = createExchange("/actuator/health", clientIp);
            StepVerifier.create(filter.filter(exceededExchange, chain))
                    .verifyComplete();

            assertThat(exceededExchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // Retry-After reports the remaining window, so it is positive and never
            // longer than the window itself.
            String retryAfter = exceededExchange.getResponse().getHeaders().getFirst("Retry-After");
            assertThat(retryAfter).isNotNull();
            assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
        }

        @Test
        @DisplayName("should track IPs independently")
        void shouldTrackIpsIndependently() {
            exhaust("/actuator/health", "10.0.0.1", 3);

            MockServerWebExchange otherIpExchange = createExchange("/actuator/health", "10.0.0.2");
            StepVerifier.create(filter.filter(otherIpExchange, chain))
                    .verifyComplete();

            assertThat(otherIpExchange.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("should bucket each path prefix separately for the same IP")
        void shouldBucketPathsSeparately() {
            String clientIp = "10.0.0.7";
            exhaust("/actuator/health", clientIp, 3);

            // Health is exhausted for this IP; the webhook path must be unaffected.
            MockServerWebExchange webhook =
                    createExchange("/api/billing/webhooks/stripe/tenant-1", clientIp);
            StepVerifier.create(filter.filter(webhook, chain)).verifyComplete();

            assertThat(webhook.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("should return JSON error body on 429")
        void shouldReturnJsonErrorBodyOn429() {
            String clientIp = "10.0.0.50";
            exhaust("/actuator/health", clientIp, 3);

            MockServerWebExchange exceededExchange = createExchange("/actuator/health", clientIp);
            StepVerifier.create(filter.filter(exceededExchange, chain))
                    .verifyComplete();

            assertThat(exceededExchange.getResponse().getHeaders().getFirst("Content-Type"))
                    .isEqualTo("application/json");
        }
    }

    @Nested
    @DisplayName("CIDR exemption")
    class Exemption {

        @Test
        @DisplayName("should never limit an IP inside an exempt range")
        void shouldNotLimitExemptRange() {
            IpRateLimitFilter exempting = newFilter(List.of("10.0.0.0/8"));

            // Well past the budget of 3.
            for (int i = 0; i < 10; i++) {
                MockServerWebExchange exchange = createExchange("/actuator/health", "10.1.2.3");
                StepVerifier.create(exempting.filter(exchange, chain)).verifyComplete();
                assertThat(exchange.getResponse().getStatusCode())
                        .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
            // Exempt traffic is not tracked at all.
            assertThat(exempting.getTrackedIpCount()).isZero();
        }

        @Test
        @DisplayName("should still limit an IP outside the exempt range")
        void shouldLimitNonExemptRange() {
            IpRateLimitFilter exempting = newFilter(List.of("10.0.0.0/8"));

            for (int i = 0; i < 3; i++) {
                StepVerifier.create(
                        exempting.filter(createExchange("/actuator/health", "192.0.2.5"), chain))
                        .verifyComplete();
            }
            MockServerWebExchange exceeded = createExchange("/actuator/health", "192.0.2.5");
            StepVerifier.create(exempting.filter(exceeded, chain)).verifyComplete();

            assertThat(exceeded.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("should accept a bare address as a single-host exemption")
        void shouldAcceptBareAddress() {
            IpRateLimitFilter exempting = newFilter(List.of("203.0.113.7"));

            for (int i = 0; i < 10; i++) {
                MockServerWebExchange exchange = createExchange("/actuator/health", "203.0.113.7");
                StepVerifier.create(exempting.filter(exchange, chain)).verifyComplete();
            }
            MockServerWebExchange neighbour = createExchange("/actuator/health", "203.0.113.8");
            StepVerifier.create(exempting.filter(neighbour, chain)).verifyComplete();

            assertThat(neighbour.getResponse().getStatusCode())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exempting.getTrackedIpCount()).isEqualTo(1); // only the neighbour
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

    private void exhaust(String path, String clientIp, int budget) {
        for (int i = 0; i < budget; i++) {
            StepVerifier.create(filter.filter(createExchange(path, clientIp), chain))
                    .verifyComplete();
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
