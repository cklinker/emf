package io.kelta.gateway.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpResolver Tests")
class ClientIpResolverTest {

    @Test
    @DisplayName("takes the leftmost X-Forwarded-For hop when trusted")
    void takesLeftmostForwardedHop() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/x")
                .header("X-Forwarded-For", "203.0.113.50, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 4711))
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("ignores X-Forwarded-For when trust is disabled")
    void ignoresForwardedWhenUntrusted() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/x")
                .header("X-Forwarded-For", "203.0.113.50")
                .remoteAddress(new InetSocketAddress("192.168.1.7", 4711))
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("192.168.1.7");
    }

    @Test
    @DisplayName("falls back to the socket address when no X-Forwarded-For")
    void fallsBackToSocketAddress() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/x")
                .remoteAddress(new InetSocketAddress("198.51.100.9", 4711))
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("normalizes brackets and IPv6 scope suffixes")
    void normalizes() {
        assertThat(ClientIpResolver.normalizeIp(" [::1] ")).isEqualTo("::1");
        assertThat(ClientIpResolver.normalizeIp("fe80::1%eth0")).isEqualTo("fe80::1");
        assertThat(ClientIpResolver.normalizeIp("  ")).isNull();
        assertThat(ClientIpResolver.normalizeIp(null)).isNull();
    }
}
