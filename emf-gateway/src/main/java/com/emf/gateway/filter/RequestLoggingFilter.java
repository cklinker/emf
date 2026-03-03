package com.emf.gateway.filter;

import com.emf.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Global filter for logging all gateway requests.
 *
 * This filter logs comprehensive information about each request including:
 * - Timestamp
 * - HTTP method
 * - Request path
 * - Response status code
 * - Request duration in milliseconds
 * - Correlation ID (if present)
 *
 * Also records Micrometer metrics:
 * - {@code emf.gateway.requests} timer (tenant, method, status, route)
 * - {@code emf.gateway.requests.active} gauge (tenant)
 *
 * The filter runs with the lowest priority (Integer.MAX_VALUE) to ensure
 * it captures the final response status after all other filters have executed.
 *
 * Validates: Requirements 12.6
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_START_TIME_ATTR = "requestStartTime";

    /** Exchange attribute set by RouteAuthorizationFilter with the matched collection name. */
    public static final String ROUTE_ATTR = "gateway.route";

    private final GatewayMetrics metrics;

    public RequestLoggingFilter(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Record start time
        Instant startTime = Instant.now();
        exchange.getAttributes().put(REQUEST_START_TIME_ATTR, startTime);

        // Increment active requests gauge
        String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
        metrics.incrementActiveRequests(tenantSlug);

        ServerHttpRequest request = exchange.getRequest();

        // Log request initiation
        log.debug("Request started: {} {}", request.getMethod(), request.getPath());

        // Continue filter chain and log on completion
        return chain.filter(exchange)
            .doFinally(signalType -> {
                logRequestCompletion(exchange, startTime);
                metrics.decrementActiveRequests(tenantSlug);
            });
    }

    /**
     * Logs the completion of a request with all relevant details.
     */
    private void logRequestCompletion(ServerWebExchange exchange, Instant startTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Calculate duration
        Duration duration = Duration.between(startTime, Instant.now());
        long durationMs = duration.toMillis();

        // Extract status code (may be null if request failed before response)
        Integer statusCode = response.getStatusCode() != null
            ? response.getStatusCode().value()
            : null;

        // Extract correlation ID if present
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");

        // Record request timer metric
        String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
        String method = request.getMethod() != null ? request.getMethod().name() : "unknown";
        String status = statusCode != null ? String.valueOf(statusCode) : "unknown";
        String route = exchange.getAttribute(ROUTE_ATTR);
        metrics.recordRequest(tenantSlug, method, status, route, duration);

        // Log structured request information
        if (statusCode != null && statusCode >= 400) {
            // Log errors and client errors at WARN level
            log.warn("Request completed: method={}, path={}, status={}, duration={}ms, correlationId={}",
                request.getMethod(),
                request.getPath(),
                statusCode,
                durationMs,
                correlationId != null ? correlationId : "none");
        } else {
            // Log successful requests at INFO level
            log.info("Request completed: method={}, path={}, status={}, duration={}ms, correlationId={}",
                request.getMethod(),
                request.getPath(),
                statusCode != null ? statusCode : "unknown",
                durationMs,
                correlationId != null ? correlationId : "none");
        }
    }

    @Override
    public int getOrder() {
        // Run last to capture final response status
        return Integer.MAX_VALUE;
    }
}
