package com.emf.worker.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enriches MDC and OTEL spans with tenant/user context from gateway headers.
 * Runs very early (HIGHEST_PRECEDENCE + 5) so all downstream code has context available.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MdcEnrichmentFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader("X-Tenant-ID");
            String tenantSlug = request.getHeader("X-Tenant-Slug");
            String userId = request.getHeader("X-User-Id");
            String userEmail = request.getHeader("X-Forwarded-User");
            String correlationId = request.getHeader("X-Correlation-ID");

            if (tenantId != null) MDC.put("tenantId", tenantId);
            if (tenantSlug != null) MDC.put("tenantSlug", tenantSlug);
            if (userId != null) MDC.put("userId", userId);
            if (userEmail != null) MDC.put("userEmail", userEmail);
            if (correlationId != null) MDC.put("correlationId", correlationId);

            // Enrich current OTEL span with custom attributes
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                if (tenantId != null) span.setAttribute("emf.tenant.id", tenantId);
                if (tenantSlug != null) span.setAttribute("emf.tenant.slug", tenantSlug);
                if (userId != null) span.setAttribute("emf.user.id", userId);
                if (userEmail != null) span.setAttribute("emf.user.email", userEmail);
                if (correlationId != null) span.setAttribute("emf.correlation.id", correlationId);
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("tenantSlug");
            MDC.remove("userId");
            MDC.remove("userEmail");
            MDC.remove("correlationId");
        }
    }
}
