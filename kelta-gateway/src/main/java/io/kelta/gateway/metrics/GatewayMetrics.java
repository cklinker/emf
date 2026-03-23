package io.kelta.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized metrics service for the Kelta API Gateway.
 *
 * <p>Encapsulates all custom Micrometer metric definitions and recording methods.
 * Filters and services inject this component to record metrics without
 * coupling to Micrometer directly.
 *
 * <h3>Metrics published:</h3>
 * <ol>
 *   <li>{@code kelta.gateway.requests} — Timer (tenant, method, status, route)</li>
 *   <li>{@code kelta.gateway.requests.active} — Gauge (tenant)</li>
 *   <li>{@code kelta.gateway.auth.failures} — Counter (tenant, reason)</li>
 *   <li>{@code kelta.gateway.authz.denied} — Counter (tenant, route, method)</li>
 *   <li>{@code kelta.gateway.ratelimit.exceeded} — Counter (tenant)</li>
 *   <li>{@code kelta.gateway.ratelimit.remaining.ratio} — Gauge (tenant)</li>
 *   <li>{@code kelta.gateway.permissions.resolve} — Timer (tenant, source)</li>
 *   <li>{@code kelta.gateway.tenant.resolution} — Counter (method, result)</li>
 *   <li>{@code kelta.gateway.errors} — Counter (tenant, status, error_code)</li>
 * </ol>
 */
@Component
public class GatewayMetrics {

    private static final String TAG_TENANT = "tenant";
    private static final String TAG_METHOD = "method";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ROUTE = "route";
    private static final String TAG_REASON = "reason";
    private static final String TAG_SOURCE = "source";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ERROR_CODE = "error_code";
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    /** Active request gauges keyed by tenant slug. */
    private final ConcurrentHashMap<String, AtomicInteger> activeRequests = new ConcurrentHashMap<>();

    /** Rate limit remaining ratio gauges keyed by tenant slug. */
    private final ConcurrentHashMap<String, AtomicInteger> rateLimitRemainingPct = new ConcurrentHashMap<>();

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // -----------------------------------------------------------------------
    // 1. kelta.gateway.requests (Timer)
    // -----------------------------------------------------------------------

    /**
     * Records the duration of a completed gateway request.
     *
     * @param tenant tenant slug (or "unknown")
     * @param method HTTP method (GET, POST, etc.)
     * @param status HTTP status code as string ("200", "404", etc.)
     * @param route  collection name from the matched route (or "unknown")
     * @param duration request duration
     */
    public void recordRequest(String tenant, String method, String status, String route, Duration duration) {
        Timer.builder("kelta.gateway.requests")
                .tag(TAG_TENANT, safe(tenant))
                .tag(TAG_METHOD, safe(method))
                .tag(TAG_STATUS, safe(status))
                .tag(TAG_ROUTE, safe(route))
                .register(registry)
                .record(duration);
    }

    // -----------------------------------------------------------------------
    // 2. kelta.gateway.requests.active (Gauge)
    // -----------------------------------------------------------------------

    /**
     * Increments the active request gauge for a tenant.
     */
    public void incrementActiveRequests(String tenant) {
        getOrCreateActiveGauge(safe(tenant)).incrementAndGet();
    }

    /**
     * Decrements the active request gauge for a tenant.
     */
    public void decrementActiveRequests(String tenant) {
        getOrCreateActiveGauge(safe(tenant)).decrementAndGet();
    }

    private AtomicInteger getOrCreateActiveGauge(String tenant) {
        return activeRequests.computeIfAbsent(tenant, t -> {
            AtomicInteger gauge = new AtomicInteger(0);
            registry.gauge("kelta.gateway.requests.active", io.micrometer.core.instrument.Tags.of(TAG_TENANT, t), gauge);
            return gauge;
        });
    }

    // -----------------------------------------------------------------------
    // 3. kelta.gateway.auth.failures (Counter)
    // -----------------------------------------------------------------------

    /**
     * Records an authentication failure.
     *
     * @param tenant tenant slug (or "unknown")
     * @param reason failure reason (missing_token, invalid_format, invalid_token, invalid_claims)
     */
    public void recordAuthFailure(String tenant, String reason) {
        Counter.builder("kelta.gateway.auth.failures")
                .tag(TAG_TENANT, safe(tenant))
                .tag(TAG_REASON, safe(reason))
                .register(registry)
                .increment();
    }

    // -----------------------------------------------------------------------
    // 4. kelta.gateway.authz.denied (Counter)
    // -----------------------------------------------------------------------

    /**
     * Records an authorization denial.
     *
     * @param tenant tenant slug (or "unknown")
     * @param route  collection name (or "unknown")
     * @param method HTTP method
     */
    public void recordAuthzDenied(String tenant, String route, String method) {
        Counter.builder("kelta.gateway.authz.denied")
                .tag(TAG_TENANT, safe(tenant))
                .tag(TAG_ROUTE, safe(route))
                .tag(TAG_METHOD, safe(method))
                .register(registry)
                .increment();
    }

    // -----------------------------------------------------------------------
    // 5. kelta.gateway.ratelimit.exceeded (Counter)
    // -----------------------------------------------------------------------

    /**
     * Records a rate limit exceeded event for a tenant.
     */
    public void recordRateLimitExceeded(String tenant) {
        Counter.builder("kelta.gateway.ratelimit.exceeded")
                .tag(TAG_TENANT, safe(tenant))
                .register(registry)
                .increment();
    }

    // -----------------------------------------------------------------------
    // 6. kelta.gateway.ratelimit.remaining.ratio (Gauge)
    // -----------------------------------------------------------------------

    /**
     * Updates the rate limit remaining ratio gauge for a tenant.
     * Stored as a percentage (0–100) to avoid floating-point gauges.
     *
     * @param tenant    tenant slug
     * @param remaining remaining requests in the current window
     * @param limit     maximum requests in the current window
     */
    public void recordRateLimitRemaining(String tenant, long remaining, long limit) {
        String safeTenant = safe(tenant);
        int pct = limit > 0 ? (int) (remaining * 100 / limit) : 0;
        getOrCreateRateLimitGauge(safeTenant).set(pct);
    }

    private AtomicInteger getOrCreateRateLimitGauge(String tenant) {
        return rateLimitRemainingPct.computeIfAbsent(tenant, t -> {
            AtomicInteger gauge = new AtomicInteger(0);
            registry.gauge("kelta.gateway.ratelimit.remaining.ratio",
                    io.micrometer.core.instrument.Tags.of(TAG_TENANT, t), gauge);
            return gauge;
        });
    }

    // -----------------------------------------------------------------------
    // 7. kelta.gateway.permissions.resolve (Timer)
    // -----------------------------------------------------------------------

    /**
     * Records the time taken to resolve permissions.
     *
     * @param tenant   tenant ID or slug
     * @param source   resolution source ("cache" or "worker")
     * @param duration time taken
     */
    public void recordPermissionResolve(String tenant, String source, Duration duration) {
        Timer.builder("kelta.gateway.permissions.resolve")
                .tag(TAG_TENANT, safe(tenant))
                .tag(TAG_SOURCE, safe(source))
                .register(registry)
                .record(duration);
    }

    // -----------------------------------------------------------------------
    // 8. kelta.gateway.tenant.resolution (Counter)
    // -----------------------------------------------------------------------

    /**
     * Records a tenant resolution attempt.
     *
     * @param method resolution method ("slug", "header", "none")
     * @param result outcome ("success", "not_found", "skipped")
     */
    public void recordTenantResolution(String method, String result) {
        Counter.builder("kelta.gateway.tenant.resolution")
                .tag(TAG_METHOD, safe(method))
                .tag(TAG_RESULT, safe(result))
                .register(registry)
                .increment();
    }

    // -----------------------------------------------------------------------
    // 9. kelta.gateway.errors (Counter)
    // -----------------------------------------------------------------------

    /**
     * Records an error handled by the global error handler.
     *
     * @param tenant    tenant slug (or "unknown")
     * @param status    HTTP status code as string
     * @param errorCode error code (UNAUTHORIZED, FORBIDDEN, etc.)
     */
    public void recordError(String tenant, String status, String errorCode) {
        Counter.builder("kelta.gateway.errors")
                .tag(TAG_TENANT, safe(tenant))
                .tag(TAG_STATUS, safe(status))
                .tag(TAG_ERROR_CODE, safe(errorCode))
                .register(registry)
                .increment();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String safe(String value) {
        return value != null && !value.isBlank() ? value : UNKNOWN;
    }
}
