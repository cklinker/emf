package io.kelta.gateway.filter;

import io.kelta.gateway.metrics.GatewayMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TenantResolutionFilter Tests")
class TenantResolutionFilterTest {

    private GatewayMetrics metrics;
    private TenantResolutionFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        metrics = mock(GatewayMetrics.class);
        filter = new TenantResolutionFilter(metrics);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldHaveCorrectOrder() {
        assertEquals(-200, filter.getOrder());
    }

    @Nested
    @DisplayName("Tenant ID Resolution")
    class TenantIdResolution {

        @Test
        void shouldResolveTenantFromIdHeader() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                    .header("X-Tenant-ID", "tenant-1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertEquals("tenant-1", TenantResolutionFilter.getTenantId(exchange));
            verify(metrics).recordTenantResolution("header", "success");
        }

        @Test
        void shouldResolveTenantSlugFromHeader() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                    .header("X-Tenant-Slug", "my-company")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertNull(TenantResolutionFilter.getTenantId(exchange));
            assertEquals("my-company", TenantResolutionFilter.getTenantSlug(exchange));
            verify(metrics).recordTenantResolution("header", "success");
        }

        @Test
        void shouldResolveBothIdAndSlug() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                    .header("X-Tenant-ID", "tenant-1")
                    .header("X-Tenant-Slug", "my-company")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertEquals("tenant-1", TenantResolutionFilter.getTenantId(exchange));
            assertEquals("my-company", TenantResolutionFilter.getTenantSlug(exchange));
        }

        @Test
        void shouldSkipWhenNoTenantHeaders() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertNull(TenantResolutionFilter.getTenantId(exchange));
            assertNull(TenantResolutionFilter.getTenantSlug(exchange));
            verify(metrics).recordTenantResolution("none", "skipped");
        }

        @Test
        void shouldSkipWhenTenantAlreadyResolved() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                    .header("X-Tenant-ID", "tenant-overwrite")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put("tenantId", "already-resolved");

            filter.filter(exchange, chain).block();

            assertEquals("already-resolved", TenantResolutionFilter.getTenantId(exchange));
        }

        @Test
        void shouldTrimTenantIdWhitespace() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                    .header("X-Tenant-ID", "  tenant-1  ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertEquals("tenant-1", TenantResolutionFilter.getTenantId(exchange));
        }
    }
}
