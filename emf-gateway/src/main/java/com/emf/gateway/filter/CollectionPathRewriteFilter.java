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
import java.util.regex.Pattern;

/**
 * Rewrites collection API request paths from {@code /api/{collectionName}/...}
 * to {@code /api/collections/{collectionName}/...} so the worker's
 * {@code DynamicCollectionRouter} (mapped at {@code /api/collections}) can handle them.
 *
 * <p>Most {@code /api/} paths are straightforward: {@code /api/product/123} becomes
 * {@code /api/collections/product/123}. The "collections" system collection requires
 * special handling because its API path ({@code /api/collections}) collides with the
 * DynamicCollectionRouter's base path. When the first segment after
 * {@code /api/collections/} is a UUID, it indicates a record lookup on the
 * "collections" collection itself and must be rewritten. When it is a non-UUID name
 * (e.g., {@code products}), the path is already correctly formed for another
 * collection and must NOT be rewritten.
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
 *   /api/product?page=1                   → /api/collections/product?page=1
 *   /api/collections                      → /api/collections/collections
 *   /api/collections/{uuid}               → /api/collections/collections/{uuid}
 *   /api/collections/{uuid}/fields        → /api/collections/collections/{uuid}/fields
 *   /api/collections/products             → unchanged (already correct)
 *   /api/collections/products/123         → unchanged (already correct)
 * </pre>
 */
@Component
public class CollectionPathRewriteFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CollectionPathRewriteFilter.class);

    private static final String API_PREFIX = "/api/";
    private static final String COLLECTIONS_PREFIX = "/api/collections/";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();

        if (path == null || !path.startsWith(API_PREFIX)) {
            return chain.filter(exchange);
        }

        // For paths starting with /api/collections/, only rewrite if the next
        // segment is a UUID (indicating a record lookup on the "collections"
        // system collection itself). Non-UUID segments like "products" are
        // already correctly formed paths for other collections.
        if (path.startsWith(COLLECTIONS_PREFIX)) {
            String remainder = path.substring(COLLECTIONS_PREFIX.length());
            String firstSegment = remainder.contains("/")
                    ? remainder.substring(0, remainder.indexOf('/'))
                    : remainder;
            if (!UUID_PATTERN.matcher(firstSegment).matches()) {
                return chain.filter(exchange);
            }
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
