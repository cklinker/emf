package io.kelta.gateway.ratelimit;

import io.kelta.gateway.geo.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitExemptionService Tests")
class RateLimitExemptionServiceTest {

    private static RateLimitExemptionService service(List<String> cidrs) {
        return new RateLimitExemptionService(new ClientIpResolver(true), cidrs);
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("empty list exempts nobody")
        void emptyListExemptsNobody() {
            RateLimitExemptionService svc = service(List.of());
            assertThat(svc.isExempt("10.0.0.1")).isFalse();
            assertThat(svc.isExempt("127.0.0.1")).isFalse();
            assertThat(svc.getConfiguredRanges()).isEmpty();
        }

        @Test
        @DisplayName("null list is treated as empty")
        void nullListIsEmpty() {
            assertThat(service(null).isExempt("10.0.0.1")).isFalse();
        }

        @Test
        @DisplayName("null IP is never exempt")
        void nullIpIsNotExempt() {
            assertThat(service(List.of("10.0.0.0/8")).isExempt((String) null)).isFalse();
        }
    }

    @Nested
    @DisplayName("CIDR matching")
    class CidrMatching {

        @Test
        @DisplayName("matches an IPv4 address inside the range")
        void matchesIpv4InRange() {
            RateLimitExemptionService svc = service(List.of("10.0.0.0/8"));
            assertThat(svc.isExempt("10.1.2.3")).isTrue();
            assertThat(svc.isExempt("10.255.255.254")).isTrue();
        }

        @Test
        @DisplayName("rejects an IPv4 address outside the range")
        void rejectsIpv4OutOfRange() {
            RateLimitExemptionService svc = service(List.of("10.0.0.0/8"));
            assertThat(svc.isExempt("11.0.0.1")).isFalse();
            assertThat(svc.isExempt("192.0.2.1")).isFalse();
        }

        @Test
        @DisplayName("honours a non-byte-aligned prefix")
        void honoursNonByteAlignedPrefix() {
            RateLimitExemptionService svc = service(List.of("192.0.2.0/25"));
            assertThat(svc.isExempt("192.0.2.1")).isTrue();
            assertThat(svc.isExempt("192.0.2.127")).isTrue();
            assertThat(svc.isExempt("192.0.2.128")).isFalse();
        }

        @Test
        @DisplayName("matches IPv6 ranges")
        void matchesIpv6() {
            RateLimitExemptionService svc = service(List.of("2001:db8::/32"));
            assertThat(svc.isExempt("2001:db8::1")).isTrue();
            assertThat(svc.isExempt("2001:db9::1")).isFalse();
        }

        @Test
        @DisplayName("does not match across address families")
        void doesNotMatchAcrossFamilies() {
            assertThat(service(List.of("10.0.0.0/8")).isExempt("2001:db8::1")).isFalse();
            assertThat(service(List.of("2001:db8::/32")).isExempt("10.0.0.1")).isFalse();
        }

        @Test
        @DisplayName("supports multiple ranges")
        void supportsMultipleRanges() {
            RateLimitExemptionService svc = service(List.of("10.0.0.0/8", "192.0.2.0/24"));
            assertThat(svc.isExempt("10.9.9.9")).isTrue();
            assertThat(svc.isExempt("192.0.2.55")).isTrue();
            assertThat(svc.isExempt("198.51.100.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("Entry parsing")
    class EntryParsing {

        @Test
        @DisplayName("treats a bare IPv4 address as a single host")
        void bareIpv4IsSingleHost() {
            RateLimitExemptionService svc = service(List.of("203.0.113.7"));
            assertThat(svc.isExempt("203.0.113.7")).isTrue();
            assertThat(svc.isExempt("203.0.113.8")).isFalse();
        }

        @Test
        @DisplayName("treats a bare IPv6 address as a single host")
        void bareIpv6IsSingleHost() {
            RateLimitExemptionService svc = service(List.of("2001:db8::5"));
            assertThat(svc.isExempt("2001:db8::5")).isTrue();
            assertThat(svc.isExempt("2001:db8::6")).isFalse();
        }

        @Test
        @DisplayName("skips malformed entries but keeps the valid ones")
        void skipsMalformedEntriesKeepingValidOnes() {
            RateLimitExemptionService svc = service(Arrays.asList(
                    "not-an-ip", "10.0.0.0/99", "10.0.0.0/", "/8", "", "  ", null, "10.0.0.0/8"));
            assertThat(svc.isExempt("10.1.1.1")).isTrue();
            assertThat(svc.isExempt("192.0.2.1")).isFalse();
        }

        @Test
        @DisplayName("trims surrounding whitespace")
        void trimsWhitespace() {
            assertThat(service(List.of("  10.0.0.0/8  ")).isExempt("10.1.1.1")).isTrue();
        }
    }

    @Nested
    @DisplayName("Exchange resolution")
    class ExchangeResolution {

        @Test
        @DisplayName("resolves the client IP the limiters key on")
        void resolvesClientIp() {
            RateLimitExemptionService svc = service(List.of("203.0.113.0/24"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health")
                            .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
                            .build());

            assertThat(svc.isExempt(exchange)).isTrue();
        }

        @Test
        @DisplayName("returns false for an exchange outside every range")
        void returnsFalseForNonExemptExchange() {
            RateLimitExemptionService svc = service(List.of("203.0.113.0/24"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health")
                            .header("X-Forwarded-For", "198.51.100.9")
                            .build());

            assertThat(svc.isExempt(exchange)).isFalse();
        }

        @Test
        @DisplayName("short-circuits without resolving when no ranges are configured")
        void shortCircuitsWhenEmpty() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health")
                            .header("X-Forwarded-For", "203.0.113.50")
                            .build());

            assertThat(service(List.of()).isExempt(exchange)).isFalse();
        }
    }
}
