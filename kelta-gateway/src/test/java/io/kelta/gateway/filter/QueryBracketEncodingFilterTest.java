package io.kelta.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueryBracketEncodingFilter.
 *
 * Tests verify that:
 * - Literal brackets in the query are percent-encoded so Spring Cloud Gateway treats the
 *   URI as already-encoded and stops re-encoding the rest of the query
 * - Existing percent escapes survive the rewrite (the actual bug: %2C arrived as %252C)
 * - Queries without brackets, and requests with no query at all, pass through untouched
 * - The rewritten URI satisfies ServerWebExchangeUtils.containsEncodedParts, which is the
 *   exact predicate RouteToRequestUrlFilter uses to decide whether to re-encode
 * - The filter runs before RouteToRequestUrlFilter
 *
 * <p>Requests are built through {@code MockServerHttpRequest.method(GET, URI)} rather than
 * the {@code get(String)} overload — the latter encodes the template, which would turn the
 * {@code %2C} under test into {@code %252C} before the filter ever sees it.
 */
@DisplayName("QueryBracketEncodingFilter Tests")
class QueryBracketEncodingFilterTest {

    private QueryBracketEncodingFilter filter;
    private GatewayFilterChain chain;
    private ServerWebExchange captured;

    @BeforeEach
    void setUp() {
        filter = new QueryBracketEncodingFilter();
        chain = mock(GatewayFilterChain.class);
        captured = null;
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            captured = invocation.getArgument(0);
            return Mono.empty();
        });
    }

    private MockServerWebExchange exchangeFor(String rawUri) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, URI.create(rawUri)).build());
    }

    @Test
    @DisplayName("Should percent-encode literal brackets in the query")
    void shouldEncodeLiteralBrackets() {
        filter.filter(exchangeFor("http://api.kelta.io/api/things?page[size]=1"), chain).block();

        assertThat(captured.getRequest().getURI().getRawQuery()).isEqualTo("page%5Bsize%5D=1");
    }

    @Test
    @DisplayName("Should preserve existing percent escapes alongside brackets")
    void shouldPreserveExistingEscapes() {
        filter.filter(exchangeFor("http://api.kelta.io/api/things?sort=a%2Cb&page[size]=1"), chain).block();

        // The escape must stay single-encoded — %252C is the bug this filter exists to prevent.
        assertThat(captured.getRequest().getURI().getRawQuery()).isEqualTo("sort=a%2Cb&page%5Bsize%5D=1");
    }

    @Test
    @DisplayName("Should produce a URI that Spring Cloud Gateway treats as already encoded")
    void shouldProduceEncodedUriSoGatewayDoesNotReEncode() {
        String raw = "http://api.kelta.io/api/things?sort=a%2Cb&page[size]=1";
        // Establishes the premise: the raw bracket is why the query got re-encoded.
        assertThat(ServerWebExchangeUtils.containsEncodedParts(URI.create(raw))).isFalse();

        filter.filter(exchangeFor(raw), chain).block();

        assertThat(ServerWebExchangeUtils.containsEncodedParts(captured.getRequest().getURI())).isTrue();
    }

    @Test
    @DisplayName("Should decode back to the original parameter names and values")
    void shouldStillBindTheSameParameterNames() {
        filter.filter(
                exchangeFor("http://api.kelta.io/api/things?fields[things]=a%2Cb&filter[name][eq]=x%20y"),
                chain).block();

        // getQueryParams() decodes, mirroring what the worker's parameter binding sees.
        assertThat(captured.getRequest().getQueryParams().getFirst("fields[things]")).isEqualTo("a,b");
        assertThat(captured.getRequest().getQueryParams().getFirst("filter[name][eq]")).isEqualTo("x y");
    }

    @Test
    @DisplayName("Should pass through a bracket-free query untouched")
    void shouldPassThroughQueryWithoutBrackets() {
        MockServerWebExchange exchange = exchangeFor("http://api.kelta.io/api/things?sort=a%2Cb");

        filter.filter(exchange, chain).block();

        assertThat(captured).isSameAs(exchange);
    }

    @Test
    @DisplayName("Should pass through a request with no query at all")
    void shouldPassThroughRequestWithoutQuery() {
        MockServerWebExchange exchange = exchangeFor("http://api.kelta.io/api/things");

        filter.filter(exchange, chain).block();

        assertThat(captured).isSameAs(exchange);
    }

    @Test
    @DisplayName("Should run before RouteToRequestUrlFilter")
    void shouldRunBeforeRouteToRequestUrlFilter() {
        assertThat(filter.getOrder()).isLessThan(RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER);
    }
}
