package io.kelta.gateway.filter;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gateway-level response cache for system collection GET requests.
 *
 * <p>This filter provides a second caching layer (in addition to the worker-side
 * Caffeine cache) that eliminates network round-trips to the worker for
 * frequently-read, rarely-changing system collections.
 *
 * <p>Behavior:
 * <ol>
 *   <li><strong>Cache hit:</strong> Returns the cached response body directly,
 *       bypassing the worker entirely.</li>
 *   <li><strong>Cache miss:</strong> Forwards the request to the worker,
 *       captures the successful response body, caches it, and returns it
 *       to the client.</li>
 * </ol>
 *
 * <p>Only caches GET requests to known system collection API paths that return
 * a 200 OK response with JSON content.
 *
 * <p>Cache invalidation is driven by Kafka events consumed by
 * {@link io.kelta.gateway.listener.SystemCollectionRouteListener}.
 *
 * @since 1.0.0
 */
@Component
public class SystemCollectionResponseCacheFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionResponseCacheFilter.class);

    /**
     * Runs after authentication (-100) and rate limiting (-50) but before
     * the default gateway routing (0).
     */
    private static final int ORDER = -10;

    /**
     * System collections whose responses should be cached at the gateway layer.
     * These are high-traffic, low-mutation collections read on every page load.
     */
    public static final Set<String> CACHEABLE_COLLECTIONS = Set.of(
            "ui-pages", "ui-menus", "ui-menu-items",
            "collections", "fields",
            "tenants",
            "oidc-providers",
            "global-picklists", "picklist-values",
            "page-layouts", "layout-sections", "layout-fields",
            "layout-related-lists", "layout-assignments",
            "list-views",
            "profiles",
            "permission-sets"
    );

    /**
     * Pattern to extract collection name from API path.
     * Matches: /api/{collectionName} or /api/{collectionName}/{id}
     * Also matches: /api/{collectionName}?query=params
     */
    private static final Pattern API_PATH_PATTERN = Pattern.compile("^/api/([a-zA-Z0-9_-]+)(?:/.*)?$");

    private final GatewayCacheManager cacheManager;

    public SystemCollectionResponseCacheFilter(GatewayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Only cache GET requests
        if (exchange.getRequest().getMethod() != HttpMethod.GET) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        String collectionName = extractCollectionName(path);

        if (collectionName == null || !CACHEABLE_COLLECTIONS.contains(collectionName)) {
            return chain.filter(exchange);
        }

        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        String query = exchange.getRequest().getURI().getRawQuery();
        String cacheKey = buildCacheKey(tenantId, path, query);

        // Check cache
        Optional<byte[]> cached = cacheManager.getSystemCollectionResponse(cacheKey);
        if (cached.isPresent()) {
            log.debug("Gateway cache hit for system collection: {} (key={})", collectionName, cacheKey);
            return writeCachedResponse(exchange, cached.get());
        }

        // Cache miss — decorate response to capture body for caching
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatus statusCode = HttpStatus.resolve(
                        getDelegate().getStatusCode() != null
                                ? getDelegate().getStatusCode().value() : 0);

                // Only cache 200 OK responses
                if (statusCode != HttpStatus.OK) {
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);

                            // Cache the response body
                            cacheManager.putSystemCollectionResponse(cacheKey, content);
                            log.debug("Gateway cached response for system collection: {} ({} bytes)",
                                    collectionName, content.length);

                            // Write the original content to the client
                            return getDelegate().writeWith(
                                    Mono.just(bufferFactory.wrap(content)));
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * Extracts the collection name from an API path.
     *
     * @param path the request path (e.g., "/api/collections" or "/api/ui-pages/123")
     * @return the collection name, or null if the path doesn't match
     */
    static String extractCollectionName(String path) {
        Matcher matcher = API_PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private String buildCacheKey(String tenantId, String path, String query) {
        String key = (tenantId != null ? tenantId : "_") + ":" + path;
        if (query != null && !query.isEmpty()) {
            key += "?" + query;
        }
        return key;
    }

    private Mono<Void> writeCachedResponse(ServerWebExchange exchange, byte[] body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(body.length);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
