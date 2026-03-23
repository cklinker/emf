package io.kelta.worker.filter;

import io.kelta.runtime.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts tenant context from gateway-provided headers
 * and sets it in the ThreadLocal {@link TenantContext}.
 *
 * <p>The gateway's HeaderTransformationFilter adds {@code X-Tenant-ID} and
 * {@code X-Tenant-Slug} headers to all proxied requests. This filter reads
 * those headers and makes them available to controllers via TenantContext.
 *
 * <p>Runs with highest precedence so TenantContext is available to all
 * downstream filters and controllers.
 *
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String X_TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String X_TENANT_SLUG_HEADER = "X-Tenant-Slug";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader(X_TENANT_ID_HEADER);
            String tenantSlug = request.getHeader(X_TENANT_SLUG_HEADER);

            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId);
            }
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                TenantContext.setSlug(tenantSlug);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
