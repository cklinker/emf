package io.kelta.ai.filter;

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extracts tenant context from gateway-provided headers and binds it for the
 * duration of the request via {@link ScopedValue}. Mirrors the pattern used by
 * kelta-worker's {@code TenantContextFilter}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String X_TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String X_TENANT_SLUG_HEADER = "X-Tenant-Slug";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(X_TENANT_ID_HEADER);
        String tenantSlug = request.getHeader(X_TENANT_SLUG_HEADER);

        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasSlug = tenantSlug != null && !tenantSlug.isBlank();

        if (!hasTenant && !hasSlug) {
            filterChain.doFilter(request, response);
            return;
        }

        ScopedValue.Carrier carrier = hasTenant
                ? ScopedValue.where(TenantContext.CURRENT_TENANT, tenantId)
                : null;
        if (hasSlug) {
            carrier = (carrier == null)
                    ? ScopedValue.where(TenantContext.CURRENT_TENANT_SLUG, tenantSlug)
                    : carrier.where(TenantContext.CURRENT_TENANT_SLUG, tenantSlug);
        }

        AtomicReference<IOException> ioErr = new AtomicReference<>();
        AtomicReference<ServletException> servletErr = new AtomicReference<>();

        carrier.run(() -> {
            try {
                filterChain.doFilter(request, response);
            } catch (IOException e) {
                ioErr.set(e);
            } catch (ServletException e) {
                servletErr.set(e);
            }
        });

        if (ioErr.get() != null) {
            throw ioErr.get();
        }
        if (servletErr.get() != null) {
            throw servletErr.get();
        }
    }
}
