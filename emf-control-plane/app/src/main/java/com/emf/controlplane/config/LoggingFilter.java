package com.emf.controlplane.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that adds correlation IDs to the MDC (Mapped Diagnostic Context)
 * for all incoming requests. This enables structured logging with request tracing.
 * 
 * The filter adds the following MDC keys:
 * - requestId: A unique identifier for each request (UUID)
 * - traceId: The OpenTelemetry trace ID (from Micrometer Tracing)
 * - spanId: The OpenTelemetry span ID (from Micrometer Tracing)
 * - correlationId: Either from X-Correlation-ID header or generated requestId
 * 
 * Requirements satisfied:
 * - 13.1: Emit structured JSON logs with correlation IDs for all requests
 * - 13.2: Include traceId, spanId, and requestId in all log entries
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String USER_ID_KEY = "userId";

    public static final String X_REQUEST_ID_HEADER = "X-Request-ID";
    public static final String X_CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final Tracer tracer;

    public LoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate or extract request ID
            String requestId = extractOrGenerateRequestId(request);
            MDC.put(REQUEST_ID_KEY, requestId);

            // Extract or use request ID as correlation ID
            String correlationId = extractOrUseRequestId(request, requestId);
            MDC.put(CORRELATION_ID_KEY, correlationId);

            // Get trace and span IDs from Micrometer Tracing (OpenTelemetry)
            populateTraceContext();

            // Add request ID to response header for client correlation
            response.setHeader(X_REQUEST_ID_HEADER, requestId);
            response.setHeader(X_CORRELATION_ID_HEADER, correlationId);

            // Log request start
            if (log.isDebugEnabled()) {
                log.debug("Request started: {} {} from {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        request.getRemoteAddr());
            }

            // Continue with the filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Log request completion with timing
            long duration = System.currentTimeMillis() - startTime;
            
            if (log.isInfoEnabled()) {
                log.info("Request completed: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration);
            }

            // Clear MDC to prevent memory leaks and context pollution
            MDC.clear();
        }
    }

    /**
     * Extracts the request ID from the X-Request-ID header or generates a new UUID.
     * 
     * @param request The HTTP request
     * @return The request ID (either from header or newly generated)
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Extracts the correlation ID from the X-Correlation-ID header or uses the request ID.
     * 
     * @param request The HTTP request
     * @param requestId The request ID to use as fallback
     * @return The correlation ID
     */
    private String extractOrUseRequestId(HttpServletRequest request, String requestId) {
        String correlationId = request.getHeader(X_CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }
        return correlationId;
    }

    /**
     * Populates the MDC with trace and span IDs from Micrometer Tracing.
     * If no active span exists, generates placeholder values.
     */
    private void populateTraceContext() {
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                String traceId = currentSpan.context().traceId();
                String spanId = currentSpan.context().spanId();
                
                if (traceId != null && !traceId.isBlank()) {
                    MDC.put(TRACE_ID_KEY, traceId);
                }
                if (spanId != null && !spanId.isBlank()) {
                    MDC.put(SPAN_ID_KEY, spanId);
                }
            } else {
                // No active span - use placeholder values
                MDC.put(TRACE_ID_KEY, "no-trace");
                MDC.put(SPAN_ID_KEY, "no-span");
            }
        } else {
            // Tracer not available - use placeholder values
            MDC.put(TRACE_ID_KEY, "tracer-unavailable");
            MDC.put(SPAN_ID_KEY, "tracer-unavailable");
        }
    }

    /**
     * Determines if the filter should be applied to the request.
     * Excludes static resources and actuator endpoints from detailed logging.
     * 
     * @param request The HTTP request
     * @return true if the filter should NOT be applied
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip static resources
        return path.startsWith("/static/") ||
               path.startsWith("/favicon.ico") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".gif");
    }
}
