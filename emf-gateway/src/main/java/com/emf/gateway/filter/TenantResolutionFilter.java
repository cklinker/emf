package com.emf.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway global filter that resolves tenant context from incoming requests.
 * Runs before JwtAuthenticationFilter (-100) to ensure tenant context is available
 * for all downstream filters and services.
 *
 * Resolution strategy:
 * 1. X-Tenant-ID header (direct tenant ID)
 * 2. X-Tenant-Slug header (slug-based resolution, forwarded as-is for control-plane to resolve)
 *
 * Sets tenant info as exchange attributes and propagates headers to downstream services.
 */
@Component
public class TenantResolutionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    public static final String TENANT_ID_ATTR = "tenantId";
    public static final String TENANT_SLUG_ATTR = "tenantSlug";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Try X-Tenant-ID header first
        String tenantId = request.getHeaders().getFirst("X-Tenant-ID");
        String tenantSlug = request.getHeaders().getFirst("X-Tenant-Slug");

        if (tenantId != null && !tenantId.isBlank()) {
            exchange.getAttributes().put(TENANT_ID_ATTR, tenantId.trim());
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                exchange.getAttributes().put(TENANT_SLUG_ATTR, tenantSlug.trim());
            }
            log.debug("Resolved tenant from header: id={}, slug={}", tenantId, tenantSlug);
        } else if (tenantSlug != null && !tenantSlug.isBlank()) {
            // Only slug provided — store it for header propagation.
            // The control-plane's TenantResolutionFilter will resolve the ID.
            exchange.getAttributes().put(TENANT_SLUG_ATTR, tenantSlug.trim());
            log.debug("Resolved tenant slug from header: {}", tenantSlug);
        } else {
            log.debug("No tenant context in request to: {}", request.getPath().value());
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -200; // Before JwtAuthenticationFilter (-100)
    }

    /**
     * Gets the resolved tenant ID from the exchange attributes.
     */
    public static String getTenantId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(TENANT_ID_ATTR);
    }

    /**
     * Gets the resolved tenant slug from the exchange attributes.
     */
    public static String getTenantSlug(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(TENANT_SLUG_ATTR);
    }
}
