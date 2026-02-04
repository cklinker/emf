package com.emf.gateway.error;

import com.emf.gateway.jsonapi.JsonApiParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalErrorHandler.
 */
class GlobalErrorHandlerTest {

    private GlobalErrorHandler errorHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new GlobalErrorHandler(objectMapper);
    }

    @Test
    void testHandleAuthenticationException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException("Invalid JWT token");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":401"));
        assertTrue(responseBody.contains("\"code\":\"UNAUTHORIZED\""));
        assertTrue(responseBody.contains("Invalid JWT token"));
        assertTrue(responseBody.contains("/api/users"));
    }

    @Test
    void testHandleJwtException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/posts").build()
        );
        
        JwtException exception = new JwtException("Token expired");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":401"));
        assertTrue(responseBody.contains("\"code\":\"UNAUTHORIZED\""));
        assertTrue(responseBody.contains("Token expired"));
    }

    @Test
    void testHandleAuthorizationException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/admin").build()
        );
        
        GatewayAuthorizationException exception = new GatewayAuthorizationException("Insufficient permissions");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":403"));
        assertTrue(responseBody.contains("\"code\":\"FORBIDDEN\""));
        assertTrue(responseBody.contains("Insufficient permissions"));
        assertTrue(responseBody.contains("/api/admin"));
    }

    @Test
    void testHandleRateLimitExceededException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/data").build()
        );
        
        RateLimitExceededException exception = new RateLimitExceededException(
            "Rate limit exceeded", 
            Duration.ofSeconds(60), 
            100
        );
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":429"));
        assertTrue(responseBody.contains("\"code\":\"RATE_LIMIT_EXCEEDED\""));
        assertTrue(responseBody.contains("Rate limit exceeded"));
    }

    @Test
    void testHandleRouteNotFoundException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/unknown").build()
        );
        
        RouteNotFoundException exception = new RouteNotFoundException("No route found for path: /api/unknown");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":404"));
        assertTrue(responseBody.contains("\"code\":\"NOT_FOUND\""));
        assertTrue(responseBody.contains("No route found"));
    }

    @Test
    void testHandleResponseStatusException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/service").build()
        );
        
        ResponseStatusException exception = new ResponseStatusException(
            HttpStatus.BAD_REQUEST, 
            "Invalid request parameters"
        );
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":400"));
        assertTrue(responseBody.contains("Invalid request parameters"));
    }

    @Test
    void testHandleJsonApiParseException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/resources").build()
        );
        
        JsonApiParser.JsonApiParseException exception = new JsonApiParser.JsonApiParseException(
            "Failed to parse JSON:API response"
        );
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":500"));
        assertTrue(responseBody.contains("\"code\":\"JSONAPI_PARSE_ERROR\""));
        assertTrue(responseBody.contains("Failed to process JSON:API response"));
    }

    @Test
    void testHandleGenericException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/error").build()
        );
        
        RuntimeException exception = new RuntimeException("Unexpected error");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"status\":500"));
        assertTrue(responseBody.contains("\"code\":\"INTERNAL_ERROR\""));
        assertTrue(responseBody.contains("An unexpected error occurred"));
        // Should NOT contain the actual exception message for security
        assertFalse(responseBody.contains("Unexpected error"));
    }

    @Test
    void testCorrelationIdFromHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test")
                .header("X-Correlation-ID", "test-correlation-123")
                .build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException("Test error");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"correlationId\":\"test-correlation-123\""));
    }

    @Test
    void testCorrelationIdGenerated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException("Test error");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"correlationId\":"));
        // Should contain a UUID-like string
        assertTrue(responseBody.matches(".*\"correlationId\":\"[a-f0-9-]{36}\".*"));
    }

    @Test
    void testAuthenticationExceptionWithNullMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException(null);
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"message\":\"Authentication failed\""));
    }

    @Test
    void testAuthorizationExceptionWithNullMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/admin").build()
        );
        
        GatewayAuthorizationException exception = new GatewayAuthorizationException(null);
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"message\":\"Access denied\""));
    }

    @Test
    void testRateLimitExceptionWithNullMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/data").build()
        );
        
        RateLimitExceededException exception = new RateLimitExceededException(
            null, 
            Duration.ofSeconds(30), 
            50
        );
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"message\":\"Rate limit exceeded\""));
    }

    @Test
    void testRouteNotFoundExceptionWithNullMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/unknown").build()
        );
        
        RouteNotFoundException exception = new RouteNotFoundException(null);
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        assertTrue(responseBody.contains("\"message\":\"Route not found\""));
    }

    @Test
    void testContentTypeIsJson() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException("Test");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    void testErrorResponseStructure() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").build()
        );
        
        GatewayAuthenticationException exception = new GatewayAuthenticationException("Invalid token");
        
        Mono<Void> result = errorHandler.handle(exchange, exception);
        
        StepVerifier.create(result)
            .verifyComplete();
        
        String responseBody = getResponseBody(exchange);
        
        // Verify all required fields are present
        assertTrue(responseBody.contains("\"status\":"));
        assertTrue(responseBody.contains("\"code\":"));
        assertTrue(responseBody.contains("\"message\":"));
        assertTrue(responseBody.contains("\"timestamp\":"));
        assertTrue(responseBody.contains("\"path\":"));
        assertTrue(responseBody.contains("\"correlationId\":"));
    }

    /**
     * Helper method to extract response body as string.
     */
    private String getResponseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
