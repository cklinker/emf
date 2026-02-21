package com.emf.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Global filter that applies in-memory IP-based rate limiting to unauthenticated endpoints.
 * Uses a ConcurrentHashMap with periodic cleanup instead of Redis to avoid external dependencies
 * for basic abuse prevention on public endpoints.
 *
 * Rate-limited paths (unauthenticated endpoints):
 * <ul>
 *   <li>/control/bootstrap</li>
 *   <li>/control/ui-bootstrap</li>
 *   <li>/control/tenants/slug-map</li>
 * </ul>
 *
 * Rate limit: 100 requests per 60-second sliding window per IP.
 * Returns 429 Too Many Requests if the limit is exceeded.
 *
 * Order: -150 (runs before JwtAuthenticationFilter at -100).
 */
@Component
public class IpRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);

    static final int MAX_REQUESTS_PER_WINDOW = 100;
    static final long WINDOW_MILLIS = 60_000L; // 60 seconds
    private static final long CLEANUP_INTERVAL_SECONDS = 120L;

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/control/bootstrap",
            "/control/ui-bootstrap",
            "/control/tenants/slug-map"
    );

    // IP -> timestamps of requests within the current window
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public IpRateLimitFilter() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ip-rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupStaleEntries,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        log.info("IpRateLimitFilter initialized: {} req/{} sec for unauthenticated endpoints, cleanup every {} sec",
                MAX_REQUESTS_PER_WINDOW, WINDOW_MILLIS / 1000, CLEANUP_INTERVAL_SECONDS);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!isRateLimitedPath(path)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        long now = Instant.now().toEpochMilli();

        Deque<Long> timestamps = requestLog.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        // Evict timestamps outside the current window
        long windowStart = now - WINDOW_MILLIS;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
            log.warn("IP rate limit exceeded for {} on path {}: {} requests in window",
                    clientIp, path, timestamps.size());
            return tooManyRequests(exchange, clientIp);
        }

        timestamps.addLast(now);
        return chain.filter(exchange);
    }

    /**
     * Determines whether the given path should be rate-limited.
     */
    static boolean isRateLimitedPath(String path) {
        return RATE_LIMITED_PATHS.contains(path);
    }

    /**
     * Resolves the client IP from X-Forwarded-For header or the remote address.
     */
    String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take the first IP in the chain (original client)
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * Removes stale entries from the request log (IPs that have no recent requests).
     */
    void cleanupStaleEntries() {
        long windowStart = Instant.now().toEpochMilli() - WINDOW_MILLIS;
        int removed = 0;

        Iterator<Map.Entry<String, Deque<Long>>> it = requestLog.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> timestamps = entry.getValue();

            // Evict old timestamps
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            // Remove entirely empty entries
            if (timestamps.isEmpty()) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("IP rate limit cleanup: removed {} stale entries, {} active IPs remaining",
                    removed, requestLog.size());
        }
    }

    /**
     * Returns a 429 Too Many Requests response.
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String clientIp) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));

        String errorJson = String.format(
                "{\"error\":{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Try again later.\",\"path\":\"%s\"}}",
                exchange.getRequest().getPath().value()
        );

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return -150; // Run before JwtAuthenticationFilter (-100)
    }

    /**
     * Returns the current number of tracked IPs (for testing/monitoring).
     */
    int getTrackedIpCount() {
        return requestLog.size();
    }

    /**
     * Clears all tracked request data (for testing).
     */
    void clearAll() {
        requestLog.clear();
    }
}
