package io.kelta.gateway.filter;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.geo.ClientIpResolver;
import io.kelta.gateway.geo.GeoIpLookupService;
import io.kelta.gateway.geo.GeoResult;
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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GeoEnrichmentFilter Tests")
class GeoEnrichmentFilterTest {

    private static final GeoResult LISBON =
            new GeoResult("PT", "Lisbon", "Cascais", 38.6979, -9.4207, 20);

    private GeoIpLookupService lookupService;
    private GeoEnrichmentFilter filter;
    private GatewayFilterChain chain;
    private final ServerWebExchange[] captured = new ServerWebExchange[1];

    @BeforeEach
    void setUp() {
        lookupService = mock(GeoIpLookupService.class);
        filter = new GeoEnrichmentFilter(new ClientIpResolver(true), lookupService, true);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return Mono.empty();
        });
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/records")
                .header("X-Forwarded-For", "203.0.113.50")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 4711))
                .build());
    }

    @Test
    @DisplayName("sets X-Geo-* headers and the exchange attribute on a lookup hit")
    void setsHeadersAndAttribute() {
        when(lookupService.lookup("203.0.113.50")).thenReturn(Optional.of(LISBON));
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = captured[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-Geo-Country")).isEqualTo("PT");
        assertThat(headers.getFirst("X-Geo-Region")).isEqualTo("Lisbon");
        assertThat(headers.getFirst("X-Geo-City")).isEqualTo("Cascais");
        assertThat(headers.getFirst("X-Geo-Lat")).isEqualTo("38.6979");
        assertThat(headers.getFirst("X-Geo-Lon")).isEqualTo("-9.4207");
        assertThat(headers.getFirst("X-Geo-Accuracy-Km")).isEqualTo("20");
        assertThat(captured[0].<GeoResult>getAttribute(GeoEnrichmentFilter.GEO_ATTRIBUTE))
                .isEqualTo(LISBON);
    }

    @Test
    @DisplayName("percent-encodes non-ASCII city names into header-safe values")
    void percentEncodesCityNames() {
        when(lookupService.lookup("203.0.113.50")).thenReturn(Optional.of(
                new GeoResult("DE", "Bayern", "München", null, null, null)));
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Geo-City"))
                .isEqualTo("M%C3%BCnchen");
        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Geo-Lat")).isNull();
    }

    @Test
    @DisplayName("passes through untouched when the lookup is empty (fail-open)")
    void passesThroughOnEmptyLookup() {
        when(lookupService.lookup(any())).thenReturn(Optional.empty());
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(captured[0]).isSameAs(exchange);
        assertThat(captured[0].getRequest().getHeaders().getFirst("X-Geo-Country")).isNull();
    }

    @Test
    @DisplayName("passes through when disabled")
    void passesThroughWhenDisabled() {
        GeoEnrichmentFilter disabled =
                new GeoEnrichmentFilter(new ClientIpResolver(true), lookupService, false);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(disabled.filter(exchange, chain)).verifyComplete();

        assertThat(captured[0]).isSameAs(exchange);
    }

    @Test
    @DisplayName("carries the country onto the principal for Cerbos")
    void enrichesPrincipal() {
        when(lookupService.lookup("203.0.113.50")).thenReturn(Optional.of(LISBON));
        MockServerWebExchange exchange = exchange();
        GatewayPrincipal principal = new GatewayPrincipal(
                "user@example.com", List.of("users"), Map.of());
        exchange.getAttributes().put("gateway.principal", principal);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        GatewayPrincipal enriched = captured[0].getAttribute("gateway.principal");
        assertThat(enriched).isNotNull();
        assertThat(enriched.getGeoCountry()).isEqualTo("PT");
        assertThat(enriched.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("runs after identity resolution (-50) and before the IP allowlist (-40)")
    void orderSitsBetweenIdentityAndAllowlist() {
        assertThat(filter.getOrder()).isEqualTo(-45);
    }
}
