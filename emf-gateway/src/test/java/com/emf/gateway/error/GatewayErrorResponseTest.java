package com.emf.gateway.error;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GatewayErrorResponse.
 */
class GatewayErrorResponseTest {

    @Test
    void testDefaultConstructor() {
        GatewayErrorResponse response = new GatewayErrorResponse();
        
        assertNotNull(response.getTimestamp());
        assertTrue(response.getTimestamp().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void testConstructorWithStatusCodeMessage() {
        GatewayErrorResponse response = new GatewayErrorResponse(401, "UNAUTHORIZED", "Invalid token");
        
        assertEquals(401, response.getStatus());
        assertEquals("UNAUTHORIZED", response.getCode());
        assertEquals("Invalid token", response.getMessage());
        assertNotNull(response.getTimestamp());
        assertNull(response.getPath());
        assertNull(response.getCorrelationId());
    }

    @Test
    void testConstructorWithPath() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            403, "FORBIDDEN", "Access denied", "/api/users"
        );
        
        assertEquals(403, response.getStatus());
        assertEquals("FORBIDDEN", response.getCode());
        assertEquals("Access denied", response.getMessage());
        assertEquals("/api/users", response.getPath());
        assertNotNull(response.getTimestamp());
        assertNull(response.getCorrelationId());
    }

    @Test
    void testConstructorWithCorrelationId() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            429, "RATE_LIMIT_EXCEEDED", "Too many requests", "/api/posts", "corr-123"
        );
        
        assertEquals(429, response.getStatus());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getCode());
        assertEquals("Too many requests", response.getMessage());
        assertEquals("/api/posts", response.getPath());
        assertEquals("corr-123", response.getCorrelationId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSetters() {
        GatewayErrorResponse response = new GatewayErrorResponse();
        Instant now = Instant.now();
        
        response.setStatus(500);
        response.setCode("INTERNAL_ERROR");
        response.setMessage("Something went wrong");
        response.setPath("/api/error");
        response.setCorrelationId("corr-456");
        response.setTimestamp(now);
        
        assertEquals(500, response.getStatus());
        assertEquals("INTERNAL_ERROR", response.getCode());
        assertEquals("Something went wrong", response.getMessage());
        assertEquals("/api/error", response.getPath());
        assertEquals("corr-456", response.getCorrelationId());
        assertEquals(now, response.getTimestamp());
    }

    @Test
    void testAuthenticationError() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            401, "UNAUTHORIZED", "Missing or invalid JWT token", "/api/users", "req-001"
        );
        
        assertEquals(401, response.getStatus());
        assertEquals("UNAUTHORIZED", response.getCode());
        assertTrue(response.getMessage().contains("JWT"));
    }

    @Test
    void testAuthorizationError() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            403, "FORBIDDEN", "Insufficient permissions", "/api/admin", "req-002"
        );
        
        assertEquals(403, response.getStatus());
        assertEquals("FORBIDDEN", response.getCode());
        assertTrue(response.getMessage().contains("permissions"));
    }

    @Test
    void testRateLimitError() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            429, "RATE_LIMIT_EXCEEDED", "Request limit exceeded", "/api/data", "req-003"
        );
        
        assertEquals(429, response.getStatus());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getCode());
        assertTrue(response.getMessage().contains("limit"));
    }

    @Test
    void testNotFoundError() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            404, "NOT_FOUND", "Route not found", "/api/unknown", "req-004"
        );
        
        assertEquals(404, response.getStatus());
        assertEquals("NOT_FOUND", response.getCode());
        assertTrue(response.getMessage().contains("not found"));
    }

    @Test
    void testInternalError() {
        GatewayErrorResponse response = new GatewayErrorResponse(
            500, "INTERNAL_ERROR", "An unexpected error occurred", "/api/service", "req-005"
        );
        
        assertEquals(500, response.getStatus());
        assertEquals("INTERNAL_ERROR", response.getCode());
        assertTrue(response.getMessage().contains("error"));
    }
}
