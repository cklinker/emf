package io.kelta.worker.filter;

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

            // Capture the actual request path for Jaeger visibility.
            // The OTEL agent uses Spring MVC route templates for span names
            // (e.g., "GET /api/{collectionName}"), which is good for grouping
            // but makes individual traces hard to identify. Adding the concrete
            // path as an attribute makes it easy to find specific requests.
            String requestPath = request.getRequestURI();
            String requestMethod = request.getMethod();
            String queryString = request.getQueryString();

            // Enrich current OTEL span with custom attributes
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                span.setAttribute("http.url.path", requestPath);
                if (queryString != null) span.setAttribute("http.url.query", queryString);
                if (tenantId != null) span.setAttribute("kelta.tenant.id", tenantId);
                if (tenantSlug != null) span.setAttribute("kelta.tenant.slug", tenantSlug);
                if (userId != null) span.setAttribute("kelta.user.id", userId);
                if (userEmail != null) span.setAttribute("kelta.user.email", userEmail);
                if (correlationId != null) span.setAttribute("kelta.correlation.id", correlationId);
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
