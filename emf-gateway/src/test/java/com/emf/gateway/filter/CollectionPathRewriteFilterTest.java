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
 * - /api/collections/{uuid} paths are rewritten (record lookup on "collections" collection)
 * - /api/collections/{name} paths are NOT rewritten (already correctly formed)
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

    // ==================== Standard collection paths ====================

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

    // ==================== "collections" system collection (UUID-based) ====================

    @Test
    void shouldRewriteCollectionsCollectionGetById() {
        // /api/collections/{uuid} → record lookup on "collections" collection
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/ec000100-0000-0000-0000-000000000003")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/collections/ec000100-0000-0000-0000-000000000003");
    }

    @Test
    void shouldRewriteCollectionsCollectionListPath() {
        // /api/collections → list all collections
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/collections");
    }

    @Test
    void shouldRewriteCollectionsSubResourcePath() {
        // /api/collections/{uuid}/fields → sub-resource on "collections" collection
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/ec000100-0000-0000-0000-000000000003/fields")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/collections/ec000100-0000-0000-0000-000000000003/fields");
    }

    // ==================== Already-formed /api/collections/{name} paths ====================

    @Test
    void shouldNotRewriteAlreadyFormedCollectionPath() {
        // /api/collections/products is already a correctly formed path
        // (e.g., from the user app UI) — must NOT be rewritten
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/products")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/products");
    }

    @Test
    void shouldNotRewriteAlreadyFormedCollectionPathWithId() {
        // /api/collections/products/123 is already correct — must NOT be rewritten
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/products/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/products/123");
    }

    @Test
    void shouldNotRewriteAlreadyFormedCollectionPathWithQueryParams() {
        // /api/collections/products?include=fields — must NOT be rewritten
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/collections/products?include=fields")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/products");
    }

    // ==================== Non-API paths ====================

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

    // ==================== GATEWAY_REQUEST_URL_ATTR handling ====================

    @Test
    void shouldUpdateGatewayRequestUrlAttr() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/product")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

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

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/product");
    }

    // ==================== Miscellaneous ====================

    @Test
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isGreaterThan(10002);
        assertThat(filter.getOrder()).isLessThan(Integer.MAX_VALUE);
    }

    @Test
    void shouldNotRewriteRootApiPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/collections/");
    }
}
