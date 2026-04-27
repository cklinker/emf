package io.kelta.mcp.observe;

import io.kelta.mcp.auth.PatSessionStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Registers gauges for in-memory state we want to keep an eye on:
 * how many MCP sessions are active, and how many distinct rate-limit
 * buckets exist (a proxy for unique PATs that have made calls).
 */
@Component
public class SessionStoreMetrics {

    private final PatSessionStore sessionStore;
    private final RateLimiter rateLimiter;
    private final MeterRegistry registry;

    public SessionStoreMetrics(PatSessionStore sessionStore,
                               RateLimiter rateLimiter,
                               MeterRegistry registry) {
        this.sessionStore = sessionStore;
        this.rateLimiter = rateLimiter;
        this.registry = registry;
    }

    @PostConstruct
    void register() {
        Gauge.builder("mcp.sessions.active", sessionStore, PatSessionStore::size)
                .description("MCP sessions currently held in PatSessionStore")
                .register(registry);
        Gauge.builder("mcp.rate_limit.buckets", rateLimiter, RateLimiter::activeBucketCount)
                .description("Distinct PATs that have a rate-limit bucket")
                .register(registry);
    }
}
