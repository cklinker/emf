package io.kelta.gateway.filter;

import io.kelta.gateway.error.ResponseHelpers;
import io.kelta.gateway.geo.ClientIpResolver;
import io.kelta.gateway.ratelimit.RateLimitExemptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Global filter applying in-memory per-IP rate limiting to unauthenticated
 * endpoints — the paths {@code RateLimitFilter} cannot cover, because that one
 * keys on an authenticated principal and returns early when there is none.
 * In-memory (not Redis) keeps basic abuse prevention free of an external
 * dependency; consistency across replicas is a later concern.
 *
 * <p>Limited paths and their budgets come from
 * {@code kelta.gateway.rate-limit.ip-paths}, a comma-separated list of
 * {@code <path-prefix>=<requests-per-window>} entries. Matching is by
 * <b>longest prefix</b>, so {@code /api/billing/webhooks} covers
 * {@code /api/billing/webhooks/stripe/{tenantId}} while a more specific entry can
 * still override a broader one.
 *
 * <p>Each matched prefix gets its <b>own</b> counter per IP, so a burst against
 * one public endpoint cannot exhaust another endpoint's budget for that client.
 *
 * <p>IPs inside {@code kelta.gateway.rate-limit.exempt-cidrs} skip the check
 * entirely — see {@link RateLimitExemptionService}.
 *
 * <p>Returns 429 with a {@code Retry-After} reflecting the true remaining window.
 * Order: -150 (runs before JwtAuthenticationFilter at -100).
 */
@Component
public class IpRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);

    /** Fallback budget for an entry configured without an explicit limit. */
    static final int DEFAULT_REQUESTS_PER_WINDOW = 100;
    static final long WINDOW_MILLIS = 60_000L; // 60 seconds
    private static final long CLEANUP_INTERVAL_SECONDS = 120L;

    /**
     * Default budgets. The webhook allowance is deliberately generous: a payment
     * processor retries on its own schedule from a small set of shared egress
     * IPs, so a tight bucket would drop legitimate deliveries rather than abuse.
     * Operators can exempt those ranges outright via {@code exempt-cidrs}.
     */
    static final String DEFAULT_IP_PATHS = "/actuator/health=100,/api/billing/webhooks=300";

    /** path prefix -> requests permitted per window, longest prefix first. */
    private final Map<String, Integer> pathBudgets;

    // "<matched prefix>|<ip>" -> timestamps of requests within the current window
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    private final ClientIpResolver clientIpResolver;
    private final RateLimitExemptionService exemptionService;

    public IpRateLimitFilter(ClientIpResolver clientIpResolver,
                             RateLimitExemptionService exemptionService,
                             @Value("${kelta.gateway.rate-limit.ip-paths:" + DEFAULT_IP_PATHS + "}")
                             List<String> ipPaths) {
        this.clientIpResolver = clientIpResolver;
        this.exemptionService = exemptionService;
        this.pathBudgets = parsePathBudgets(ipPaths);
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
        log.info("IpRateLimitFilter initialized: {} per-IP limited path(s) {} over a {}s window, "
                        + "cleanup every {}s",
                pathBudgets.size(), pathBudgets, WINDOW_MILLIS / 1000, CLEANUP_INTERVAL_SECONDS);
    }

    /**
     * Parses {@code <prefix>=<limit>} entries into a longest-prefix-first map. A
     * malformed entry is logged and skipped — a typo must neither stop the
     * gateway booting nor silently disable limiting on the valid entries.
     */
    static Map<String, Integer> parsePathBudgets(List<String> entries) {
        if (entries == null) {
            return Map.of();
        }
        Map<String, Integer> parsed = new LinkedHashMap<>();
        entries.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                // Longest prefix first, so the first match in iteration order is
                // the most specific one.
                .sorted((a, b) -> Integer.compare(pathOf(b).length(), pathOf(a).length()))
                .forEach(entry -> {
                    String path = pathOf(entry);
                    if (path.isEmpty()) {
                        log.warn("Ignoring rate-limit path entry with empty path: '{}'", entry);
                        return;
                    }
                    int limit = DEFAULT_REQUESTS_PER_WINDOW;
                    int eq = entry.indexOf('=');
                    if (eq >= 0) {
                        try {
                            limit = Integer.parseInt(entry.substring(eq + 1).trim());
                        } catch (NumberFormatException e) {
                            log.warn("Invalid limit in rate-limit path entry '{}'; using default {}",
                                    entry, DEFAULT_REQUESTS_PER_WINDOW);
                        }
                    }
                    if (limit <= 0) {
                        log.warn("Ignoring non-positive limit in rate-limit path entry '{}'", entry);
                        return;
                    }
                    parsed.put(path, limit);
                });
        return Collections.unmodifiableMap(parsed);
    }

    private static String pathOf(String entry) {
        int eq = entry.indexOf('=');
        return (eq >= 0 ? entry.substring(0, eq) : entry).trim();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        String matched = matchPath(path);
        if (matched == null) {
            return chain.filter(exchange);
        }

        // Trusted infrastructure (uptime probes, in-cluster callers, a payment
        // processor's egress ranges) bypasses the bucket entirely.
        if (exemptionService.isExempt(exchange)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        long now = Instant.now().toEpochMilli();
        int limit = pathBudgets.getOrDefault(matched, DEFAULT_REQUESTS_PER_WINDOW);

        Deque<Long> timestamps = requestLog.computeIfAbsent(
                matched + "|" + clientIp, k -> new ConcurrentLinkedDeque<>());

        // Evict timestamps outside the current window
        long windowStart = now - WINDOW_MILLIS;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= limit) {
            log.warn("IP rate limit exceeded for {} on path {} (bucket {}): {} requests in window, limit {}",
                    clientIp, path, matched, timestamps.size(), limit);
            return tooManyRequests(exchange, timestamps.peekFirst(), now);
        }

        timestamps.addLast(now);
        return chain.filter(exchange);
    }

    /**
     * Returns the longest configured prefix matching {@code path}, or null when
     * the path is not rate limited.
     */
    String matchPath(String path) {
        if (path == null) {
            return null;
        }
        for (String prefix : pathBudgets.keySet()) {
            if (path.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * Resolves the client IP via the shared trust-aware resolver.
     */
    String resolveClientIp(ServerWebExchange exchange) {
        String ip = clientIpResolver.resolve(exchange);
        return ip != null ? ip : "unknown";
    }

    /**
     * Removes stale entries from the request log (buckets with no recent requests).
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
            log.debug("IP rate limit cleanup: removed {} stale buckets, {} active buckets remaining",
                    removed, requestLog.size());
        }
    }

    /**
     * Returns 429 with an honest {@code Retry-After}: the time until the oldest
     * request in the window ages out, not the full window length.
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, Long oldest, long now) {
        if (!ResponseHelpers.prepareJsonResponse(exchange.getResponse(), HttpStatus.TOO_MANY_REQUESTS)) {
            return Mono.empty();
        }
        long retryAfterSeconds = WINDOW_MILLIS / 1000;
        if (oldest != null) {
            long remainingMillis = Math.max(0, (oldest + WINDOW_MILLIS) - now);
            retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        }
        final long retryAfter = retryAfterSeconds;
        ResponseHelpers.applyHeaderIfWritable(exchange.getResponse(),
                () -> exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfter)));

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
     * Returns the current number of tracked buckets (for testing/monitoring).
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
