package com.emf.gateway.error;

import java.time.Duration;

/**
 * Exception thrown when rate limit is exceeded.
 * Results in HTTP 429 Too Many Requests response with Retry-After header.
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final Duration retryAfter;
    private final int limit;
    
    public RateLimitExceededException(String message, Duration retryAfter, int limit) {
        super(message);
        this.retryAfter = retryAfter;
        this.limit = limit;
    }
    
    public Duration getRetryAfter() {
        return retryAfter;
    }
    
    public int getLimit() {
        return limit;
    }
}
