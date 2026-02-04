package com.emf.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Result of a rate limit check operation.
 * 
 * Contains information about whether the request is allowed,
 * how many requests remain in the current window, and when
 * the window resets.
 */
public class RateLimitResult {
    
    private final boolean allowed;
    private final long remainingRequests;
    private final Duration retryAfter;
    
    /**
     * Creates a new RateLimitResult.
     * 
     * @param allowed Whether the request is allowed
     * @param remainingRequests Number of requests remaining in the current window
     * @param retryAfter Duration to wait before retrying (only relevant when not allowed)
     */
    public RateLimitResult(boolean allowed, long remainingRequests, Duration retryAfter) {
        this.allowed = allowed;
        this.remainingRequests = remainingRequests;
        this.retryAfter = retryAfter;
    }
    
    /**
     * Creates a result indicating the request is allowed.
     * 
     * @param remainingRequests Number of requests remaining in the current window
     * @return A RateLimitResult with allowed=true
     */
    public static RateLimitResult allowed(long remainingRequests) {
        return new RateLimitResult(true, remainingRequests, Duration.ZERO);
    }
    
    /**
     * Creates a result indicating the request is not allowed.
     * 
     * @param retryAfter Duration to wait before retrying
     * @return A RateLimitResult with allowed=false
     */
    public static RateLimitResult notAllowed(Duration retryAfter) {
        return new RateLimitResult(false, 0, retryAfter);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public long getRemainingRequests() {
        return remainingRequests;
    }
    
    public Duration getRetryAfter() {
        return retryAfter;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitResult that = (RateLimitResult) o;
        return allowed == that.allowed &&
               remainingRequests == that.remainingRequests &&
               Objects.equals(retryAfter, that.retryAfter);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(allowed, remainingRequests, retryAfter);
    }
    
    @Override
    public String toString() {
        return "RateLimitResult{" +
               "allowed=" + allowed +
               ", remainingRequests=" + remainingRequests +
               ", retryAfter=" + retryAfter +
               '}';
    }
}
