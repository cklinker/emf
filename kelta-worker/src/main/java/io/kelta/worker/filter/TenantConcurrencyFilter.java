package io.kelta.worker.filter;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.TenantConcurrencyLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-tenant concurrency cap: a single tenant can have at most N concurrent
 * in-flight requests per worker pod. Excess requests get 503 with
 * {@code Retry-After}, protecting the HikariCP pool from a noisy neighbor.
 *
 * <p>Runs after {@link TenantContextFilter} (order +10) so the tenant is bound
 * when {@link TenantConcurrencyLimiter#tryAcquire(String)} is called.
 * Skips actuator probes and the governor-limits endpoint so health + quota
 * pages stay responsive under load.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class TenantConcurrencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantConcurrencyFilter.class);

    private final TenantConcurrencyLimiter limiter;

    public TenantConcurrencyFilter(TenantConcurrencyLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
                || path.startsWith("/internal/")
                || path.equals("/api/governor-limits");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!limiter.tryAcquire(tenantId)) {
            log.info("Tenant {} concurrency limit reached ({} in flight) — rejecting request",
                    tenantId, limiter.inUsePermits(tenantId));
            response.setStatus(503);
            response.setHeader("Retry-After", "1");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"errors":[{"status":"503","code":"TENANT_CONCURRENCY_LIMIT","title":"Too many concurrent requests for this tenant; retry shortly"}]}
                """);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            limiter.release(tenantId);
        }
    }
}
