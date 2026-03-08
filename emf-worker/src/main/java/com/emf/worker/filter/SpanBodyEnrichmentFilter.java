package com.emf.worker.filter;

import com.emf.worker.interceptor.SpanIdCaptureInterceptor;
import com.emf.worker.service.RequestDataCaptureService;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Captures request/response bodies and headers, writing them both as OTEL span
 * attributes and directly to OpenSearch. The direct OpenSearch write is the primary
 * mechanism because the OTEL Java agent silently drops span attributes set via
 * Span.current().setAttribute() when trace context is propagated from upstream services.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class SpanBodyEnrichmentFilter extends OncePerRequestFilter {

    private static final Set<String> EXCLUDE_PREFIXES = Set.of(
            "/actuator", "/health"
    );

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\"(?:password|secret|token|apiKey|api_key|creditCard|credit_card|ssn|authorization)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    private final RequestDataCaptureService captureService;

    @Value("${emf.observability.request-logging.max-body-size:16384}")
    private int maxBodySize;

    @Value("${emf.observability.request-logging.enabled:true}")
    private boolean enabled;

    public SpanBodyEnrichmentFilter(RequestDataCaptureService captureService) {
        this.captureService = captureService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || shouldExclude(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, maxBodySize);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            captureData(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void captureData(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        // Extract bodies
        String requestBody = extractBody(request.getContentAsByteArray(), request.getContentType());
        String responseBody = extractBody(response.getContentAsByteArray(), response.getContentType());

        // Extract context from headers
        String tenantId = request.getHeader("X-Tenant-ID");
        String userId = request.getHeader("X-User-Id");
        String userEmail = request.getHeader("X-Forwarded-User");
        String correlationId = request.getHeader("X-Correlation-ID");

        // Try to set OTEL span attributes (works when no trace propagation)
        enrichSpan(requestBody, responseBody, tenantId, userId, userEmail, correlationId);

        // Write directly to OpenSearch (always works, bypasses OTEL agent limitations).
        // Prefer the span ID captured by SpanIdCaptureInterceptor (which runs inside the
        // DispatcherServlet scope where the OTEL server span is active). By the time this
        // filter's finally block runs, the OTEL agent has closed the server span scope and
        // Span.current() returns the propagated parent context instead.
        String traceId = (String) request.getAttribute(SpanIdCaptureInterceptor.SERVER_TRACE_ID_ATTR);
        String spanId = (String) request.getAttribute(SpanIdCaptureInterceptor.SERVER_SPAN_ID_ATTR);

        // Fallback to Span.current() for requests that don't go through the DispatcherServlet
        if (traceId == null || spanId == null) {
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                traceId = span.getSpanContext().getTraceId();
                spanId = span.getSpanContext().getSpanId();
            }
        }

        if (traceId != null && spanId != null) {

            Map<String, String> requestHeaders = RequestDataCaptureService.extractRequestHeaders(request);
            Map<String, String> responseHeaders = RequestDataCaptureService.extractResponseHeaders(response);

            captureService.captureRequestData(
                    traceId, spanId,
                    requestHeaders, responseHeaders,
                    requestBody, responseBody,
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    tenantId, userId, userEmail, correlationId
            );
        }
    }

    private String extractBody(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return null;
        }
        // Capture JSON-like content types
        if (contentType != null && (contentType.contains("json") || contentType.contains("text"))) {
            String body = new String(content, StandardCharsets.UTF_8);
            return sanitize(truncate(body));
        }
        return null;
    }

    private void enrichSpan(String requestBody, String responseBody,
                            String tenantId, String userId, String userEmail, String correlationId) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }

        if (requestBody != null) {
            span.setAttribute("http.request.body", requestBody);
        }
        if (responseBody != null) {
            span.setAttribute("http.response.body", responseBody);
        }
        if (tenantId != null) span.setAttribute("emf.tenant.id", tenantId);
        if (userId != null) span.setAttribute("emf.user.id", userId);
        if (userEmail != null) span.setAttribute("emf.user.email", userEmail);
        if (correlationId != null) span.setAttribute("emf.correlation.id", correlationId);
    }

    private boolean shouldExclude(String path) {
        return EXCLUDE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String truncate(String body) {
        if (body.length() > maxBodySize) {
            return body.substring(0, maxBodySize) + "...[truncated]";
        }
        return body;
    }

    private String sanitize(String body) {
        return SENSITIVE_FIELD_PATTERN.matcher(body).replaceAll("$1\"[REDACTED]\"");
    }
}
