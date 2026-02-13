package com.emf.gateway.error;

import com.emf.gateway.jsonapi.JsonApiParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Global error handler for the API Gateway.
 * Implements centralized error handling for all gateway errors with consistent JSON responses.
 * 
 * This handler processes:
 * - Authentication errors (401) - Missing, invalid, or expired JWT tokens
 * - Authorization errors (403) - Insufficient permissions
 * - Rate limit errors (429) - Request limit exceeded with Retry-After header
 * - Routing errors (404) - No matching route found
 * - Backend errors - Passed through unchanged
 * - Internal errors (500) - Unexpected exceptions with generic message
 * 
 * All errors are logged with full details, and sensitive information is excluded
 * from client responses for internal errors.
 * 
 * Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5
 */
@Component
@Order(-2) // Run before Spring's default error handler
public class GlobalErrorHandler implements ErrorWebExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);
    
    private final ObjectMapper objectMapper;
    
    public GlobalErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Register JavaTimeModule to handle Java 8 date/time types
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();
        String correlationId = getOrCreateCorrelationId(exchange);
        
        log.debug("Handling error for path: {}, correlationId: {}, exception: {}", 
            path, correlationId, ex.getClass().getSimpleName());
        
        GatewayErrorResponse errorResponse;
        HttpStatus status;
        
        // Handle different exception types
        if (ex instanceof GatewayAuthenticationException || ex instanceof JwtException) {
            // Authentication errors (401)
            status = HttpStatus.UNAUTHORIZED;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "UNAUTHORIZED",
                ex.getMessage() != null ? ex.getMessage() : "Authentication failed",
                path,
                correlationId
            );
            log.warn("Authentication error for path: {}, correlationId: {}, message: {}", 
                path, correlationId, ex.getMessage());
            
        } else if (ex instanceof GatewayAuthorizationException) {
            // Authorization errors (403)
            status = HttpStatus.FORBIDDEN;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "FORBIDDEN",
                ex.getMessage() != null ? ex.getMessage() : "Access denied",
                path,
                correlationId
            );
            log.warn("Authorization error for path: {}, correlationId: {}, message: {}", 
                path, correlationId, ex.getMessage());
            
        } else if (ex instanceof RateLimitExceededException) {
            // Rate limit errors (429)
            RateLimitExceededException rateLimitEx = (RateLimitExceededException) ex;
            status = HttpStatus.TOO_MANY_REQUESTS;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "RATE_LIMIT_EXCEEDED",
                ex.getMessage() != null ? ex.getMessage() : "Rate limit exceeded",
                path,
                correlationId
            );
            
            // Add Retry-After header
            long retryAfterSeconds = rateLimitEx.getRetryAfter().getSeconds();
            exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfterSeconds));
            
            log.warn("Rate limit exceeded for path: {}, correlationId: {}, retryAfter: {}s", 
                path, correlationId, retryAfterSeconds);
            
        } else if (ex instanceof RouteNotFoundException) {
            // Routing errors (404)
            status = HttpStatus.NOT_FOUND;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "Route not found",
                path,
                correlationId
            );
            log.warn("Route not found for path: {}, correlationId: {}", path, correlationId);
            
        } else if (ex instanceof ResponseStatusException) {
            // Spring's ResponseStatusException (includes backend errors)
            ResponseStatusException statusEx = (ResponseStatusException) ex;
            status = HttpStatus.resolve(statusEx.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            
            errorResponse = new GatewayErrorResponse(
                status.value(),
                status.name().replace(" ", "_"),
                statusEx.getReason() != null ? statusEx.getReason() : status.getReasonPhrase(),
                path,
                correlationId
            );
            log.warn("Response status exception for path: {}, correlationId: {}, status: {}, message: {}", 
                path, correlationId, status, statusEx.getReason());
            
        } else if (ex instanceof JsonApiParser.JsonApiParseException) {
            // JSON:API parsing errors (500)
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "JSONAPI_PARSE_ERROR",
                "Failed to process JSON:API response",
                path,
                correlationId
            );
            log.error("JSON:API parse error for path: {}, correlationId: {}", path, correlationId, ex);
            
        } else {
            // Internal errors (500) - generic message for security
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorResponse = new GatewayErrorResponse(
                status.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                path,
                correlationId
            );
            log.error("Internal error for path: {}, correlationId: {}", path, correlationId, ex);
        }
        
        // Set response status and headers
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // Serialize error response to JSON
        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response for path: {}, correlationId: {}", 
                path, correlationId, e);
            
            // Fallback to simple JSON:API error format
            String fallbackJson = String.format(
                "{\"errors\":[{\"status\":\"%d\",\"code\":\"%s\",\"detail\":\"%s\",\"meta\":{\"path\":\"%s\",\"correlationId\":\"%s\"}}]}",
                status.value(),
                errorResponse.getCode(),
                errorResponse.getMessage(),
                path,
                correlationId
            );
            byte[] bytes = fallbackJson.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
    
    /**
     * Gets the correlation ID from the exchange attributes or creates a new one.
     *
     * @param exchange the server web exchange
     * @return the correlation ID
     */
    private String getOrCreateCorrelationId(ServerWebExchange exchange) {
        // Try to get from exchange attributes first
        String correlationId = exchange.getAttribute("correlationId");
        
        if (correlationId == null) {
            // Try to get from request header
            correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        }
        
        if (correlationId == null) {
            // Generate new correlation ID
            correlationId = UUID.randomUUID().toString();
        }
        
        return correlationId;
    }
}
