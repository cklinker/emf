package com.emf.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Rewrites collection API request paths from {@code /api/{collectionName}/...}
 * to {@code /api/collections/{collectionName}/...} so the worker's
 * {@code DynamicCollectionRouter} (mapped at {@code /api/collections}) can handle them.
 *
 * <p>This applies to ALL {@code /api/} paths, including the "collections" system
 * collection itself (e.g., {@code /api/collections/{uuid}} →
 * {@code /api/collections/collections/{uuid}}). The DynamicCollectionRouter expects
 * the collection name as the first path segment after {@code /api/collections/},
 * so the "collections" collection must also follow this pattern.
 *
 * <p>This is implemented as a {@link GlobalFilter} rather than a route-level
 * {@link org.springframework.cloud.gateway.filter.GatewayFilter} because route
 * filters execute after Spring Cloud Gateway's {@code RouteToRequestUrlFilter}
 * has already constructed the upstream URL. By running as a GlobalFilter at
 * order 10100 (after {@code RouteToRequestUrlFilter} at 10002, but before
 * {@code NettyRoutingFilter} at {@code Integer.MAX_VALUE}), this filter can
 * update both the request path and the {@code GATEWAY_REQUEST_URL_ATTR} that
 * {@code NettyRoutingFilter} uses to send the actual HTTP request upstream.
 *
 * <p>Examples:
 * <pre>
 *   Gateway receives: GET /api/product?page=1
 *   Worker expects:   GET /api/collections/product?page=1
 *
 *   Gateway receives: GET /api/collections/{uuid}
 *   Worker expects:   GET /api/collections/collections/{uuid}
 * </pre>
 */
@Component
public class CollectionPathRewriteFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CollectionPathRewriteFilter.class);

    private static final String API_PREFIX = "/api/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();

        // Rewrite ALL /api/xxx paths to /api/collections/xxx.
        // This includes /api/collections/xxx → /api/collections/collections/xxx
        // because the "collections" system collection must also go through
        // DynamicCollectionRouter with collectionName="collections".
        if (path == null || !path.startsWith(API_PREFIX)) {
            return chain.filter(exchange);
        }

        // Rewrite /api/xxx → /api/collections/xxx
        String newPath = "/api/collections" + path.substring("/api".length());

        log.debug("Rewriting collection API path: '{}' → '{}'", path, newPath);

        // Mutate the request with the rewritten path
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .path(newPath)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Update GATEWAY_REQUEST_URL_ATTR so NettyRoutingFilter sends to the rewritten path.
        // RouteToRequestUrlFilter (order 10002) has already set this attribute by now.
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (routeUri != null) {
            String routeUriStr = routeUri.toString();
            URI rewrittenUri = URI.create(routeUriStr.replace(path, newPath));
            mutatedExchange.getAttributes().put(
                    ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, rewrittenUri);
            log.trace("Updated GATEWAY_REQUEST_URL_ATTR: '{}' → '{}'", routeUri, rewrittenUri);
        }

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // After RouteToRequestUrlFilter (10002) but before NettyRoutingFilter (MAX_VALUE)
        return 10100;
    }
}
