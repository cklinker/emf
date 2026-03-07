package com.emf.worker.filter;

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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Captures request/response bodies and adds them as OTEL span attributes.
 * Only processes JSON content types and truncates bodies exceeding max size.
 * Sensitive fields (passwords, tokens, etc.) are redacted.
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

    @Value("${emf.observability.request-logging.max-body-size:16384}")
    private int maxBodySize;

    @Value("${emf.observability.request-logging.enabled:true}")
    private boolean enabled;

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
            enrichSpan(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void enrichSpan(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }

        // Capture request body
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            byte[] requestBody = request.getContentAsByteArray();
            if (requestBody.length > 0) {
                String body = new String(requestBody, StandardCharsets.UTF_8);
                span.setAttribute("http.request.body", sanitize(truncate(body)));
            }
        }

        // Capture response body
        String responseContentType = response.getContentType();
        if (responseContentType != null && responseContentType.contains("application/json")) {
            byte[] responseBody = response.getContentAsByteArray();
            if (responseBody.length > 0) {
                String body = new String(responseBody, StandardCharsets.UTF_8);
                span.setAttribute("http.response.body", sanitize(truncate(body)));
            }
        }

        // Add tenant/user context from headers
        String tenantId = request.getHeader("X-Tenant-ID");
        String userId = request.getHeader("X-User-Id");
        String userEmail = request.getHeader("X-Forwarded-User");
        String correlationId = request.getHeader("X-Correlation-ID");

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
