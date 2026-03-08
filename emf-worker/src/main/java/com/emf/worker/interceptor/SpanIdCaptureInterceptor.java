package com.emf.worker.interceptor;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Captures the OTEL server span ID from within the DispatcherServlet scope
 * and stores it as a request attribute.
 *
 * <p>The OTEL Java agent creates the server span inside the DispatcherServlet,
 * so servlet filters running outside that scope (like {@code SpanBodyEnrichmentFilter})
 * see the propagated parent context instead of the worker's own server span.
 * This interceptor runs inside the DispatcherServlet scope where the correct
 * span is active, and saves the span ID for the filter to use later.
 */
@Component
public class SpanIdCaptureInterceptor implements HandlerInterceptor {

    public static final String SERVER_SPAN_ID_ATTR = "emf.server.spanId";
    public static final String SERVER_TRACE_ID_ATTR = "emf.server.traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            request.setAttribute(SERVER_SPAN_ID_ATTR, span.getSpanContext().getSpanId());
            request.setAttribute(SERVER_TRACE_ID_ATTR, span.getSpanContext().getTraceId());

            // Set user/tenant attributes on the correct server span.
            // MdcEnrichmentFilter populates these headers via MDC but sets span
            // attributes on the propagated parent (wrong span) because it runs
            // outside the DispatcherServlet scope.
            setIfPresent(span, "emf.user.id", request.getHeader("X-User-Id"));
            setIfPresent(span, "emf.user.email", request.getHeader("X-Forwarded-User"));
            setIfPresent(span, "emf.tenant.id", request.getHeader("X-Tenant-ID"));
            setIfPresent(span, "emf.tenant.slug", request.getHeader("X-Tenant-Slug"));
            setIfPresent(span, "emf.correlation.id", request.getHeader("X-Correlation-ID"));
        }
        return true;
    }

    private void setIfPresent(Span span, String key, String value) {
        if (value != null && !value.isEmpty()) {
            span.setAttribute(key, value);
        }
    }
}
