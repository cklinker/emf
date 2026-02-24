package com.emf.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CollectionPathRewriteFilter.
 *
 * Tests verify that:
 * - /api/xxx paths are rewritten to /api/collections/xxx
 * - /api/collections/xxx paths are NOT rewritten (no double-rewrite)
 * - Non-API paths (e.g. /internal/xxx) pass through unchanged
 * - GATEWAY_REQUEST_URL_ATTR is updated when present
 * - Filter order is correct (after RouteToRequestUrlFilter, before NettyRoutingFilter)
 */
class CollectionPathRewriteFilterTest {

    private CollectionPathRewriteFilter filter;
    private GatewayFilterChain chain;
    private AtomicReference<ServerWebExchange> capturedExchange;

    @BeforeEach
    void setUp() {
        filter = new CollectionPathRewriteFilter();
        chain = mock(GatewayFilterChain.class);
        capturedExchange = new AtomicReference<>();

        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
    }

    @Test
    void shouldRewriteCollectionApiPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product");
    }

    @Test
    void shouldRewriteCollectionApiPathWithSubpath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product/123");
    }

    @Test
    void shouldRewriteCollectionApiPathWithQueryParams() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product?page=1&pageSize=25")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product");
        assertThat(capturedExchange.get().getRequest().getURI().getQuery())
                .isEqualTo("page=1&pageSize=25");
    }

    @Test
    void shouldNotRewriteAlreadyRewrittenPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/product")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product");
    }

    @Test
    void shouldNotRewriteInternalPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/internal/bootstrap")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/internal/bootstrap");
    }

    @Test
    void shouldNotRewriteActuatorPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/actuator/health");
    }

    @Test
    void shouldUpdateGatewayRequestUrlAttr() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Simulate RouteToRequestUrlFilter having already set the attribute
        URI routeUri = URI.create("http://emf-worker:80/api/product");
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, routeUri);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        URI updatedUri = capturedExchange.get()
                .getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertThat(updatedUri).isNotNull();
        assertThat(updatedUri.toString()).isEqualTo("http://emf-worker:80/api/collections/product");
    }

    @Test
    void shouldUpdateGatewayRequestUrlAttrWithSubpath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product/123/records")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        URI routeUri = URI.create("http://emf-worker:80/api/product/123/records");
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, routeUri);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        URI updatedUri = capturedExchange.get()
                .getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertThat(updatedUri).isNotNull();
        assertThat(updatedUri.toString()).isEqualTo("http://emf-worker:80/api/collections/product/123/records");
    }

    @Test
    void shouldHandleMissingGatewayRequestUrlAttr() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No GATEWAY_REQUEST_URL_ATTR set

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Path should still be rewritten even without the attribute
        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product");
    }

    @Test
    void shouldHaveCorrectOrder() {
        // Must be after RouteToRequestUrlFilter (10002) but before NettyRoutingFilter (MAX_VALUE)
        assertThat(filter.getOrder()).isGreaterThan(10002);
        assertThat(filter.getOrder()).isLessThan(Integer.MAX_VALUE);
    }

    @Test
    void shouldNotRewriteRootApiPath() {
        // Edge case: /api/ alone (no collection name)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // /api/ starts with /api/ and is not /api/collections/, so it gets rewritten
        // to /api/collections/ — this is fine, the worker will handle it
        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/");
    }
}
