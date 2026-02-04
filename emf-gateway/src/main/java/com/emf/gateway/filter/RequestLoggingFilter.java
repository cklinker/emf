package com.emf.gateway.filter;

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
 * The filter runs with the lowest priority (Integer.MAX_VALUE) to ensure
 * it captures the final response status after all other filters have executed.
 * 
 * Validates: Requirements 12.6
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_START_TIME_ATTR = "requestStartTime";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Record start time
        Instant startTime = Instant.now();
        exchange.getAttributes().put(REQUEST_START_TIME_ATTR, startTime);
        
        ServerHttpRequest request = exchange.getRequest();
        
        // Log request initiation
        log.debug("Request started: {} {}", request.getMethod(), request.getPath());
        
        // Continue filter chain and log on completion
        return chain.filter(exchange)
            .doFinally(signalType -> {
                logRequestCompletion(exchange, startTime);
            });
    }
    
    /**
     * Logs the completion of a request with all relevant details.
     */
    private void logRequestCompletion(ServerWebExchange exchange, Instant startTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Calculate duration
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        
        // Extract status code (may be null if request failed before response)
        Integer statusCode = response.getStatusCode() != null 
            ? response.getStatusCode().value() 
            : null;
        
        // Extract correlation ID if present
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
        
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
