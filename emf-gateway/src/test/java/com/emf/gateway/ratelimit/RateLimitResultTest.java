package com.emf.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitResult.
 */
class RateLimitResultTest {
    
    @Test
    void testAllowedResult() {
        RateLimitResult result = RateLimitResult.allowed(10);
        
        assertTrue(result.isAllowed());
        assertEquals(10, result.getRemainingRequests());
        assertEquals(Duration.ZERO, result.getRetryAfter());
    }
    
    @Test
    void testNotAllowedResult() {
        Duration retryAfter = Duration.ofSeconds(30);
        RateLimitResult result = RateLimitResult.notAllowed(retryAfter);
        
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemainingRequests());
        assertEquals(retryAfter, result.getRetryAfter());
    }
    
    @Test
    void testConstructor() {
        Duration retryAfter = Duration.ofSeconds(60);
        RateLimitResult result = new RateLimitResult(true, 5, retryAfter);
        
        assertTrue(result.isAllowed());
        assertEquals(5, result.getRemainingRequests());
        assertEquals(retryAfter, result.getRetryAfter());
    }
    
    @Test
    void testEquality() {
        RateLimitResult result1 = RateLimitResult.allowed(10);
        RateLimitResult result2 = RateLimitResult.allowed(10);
        RateLimitResult result3 = RateLimitResult.allowed(5);
        
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
    }
    
    @Test
    void testHashCode() {
        RateLimitResult result1 = RateLimitResult.allowed(10);
        RateLimitResult result2 = RateLimitResult.allowed(10);
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }
    
    @Test
    void testToString() {
        RateLimitResult result = RateLimitResult.allowed(10);
        String str = result.toString();
        
        assertTrue(str.contains("allowed=true"));
        assertTrue(str.contains("remainingRequests=10"));
    }
}
