package com.emf.gateway.route;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for rate limiting on a route.
 * 
 * Defines the maximum number of requests allowed within a time window.
 * This is a placeholder implementation that will be enhanced in later tasks.
 */
public class RateLimitConfig {
    
    private final int requestsPerWindow;
    private final Duration windowDuration;
    
    /**
     * Creates a new RateLimitConfig.
     * 
     * @param requestsPerWindow Maximum number of requests allowed in the time window
     * @param windowDuration Duration of the time window
     */
    public RateLimitConfig(int requestsPerWindow, Duration windowDuration) {
        this.requestsPerWindow = requestsPerWindow;
        this.windowDuration = windowDuration;
    }
    
    public int getRequestsPerWindow() {
        return requestsPerWindow;
    }
    
    public Duration getWindowDuration() {
        return windowDuration;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitConfig that = (RateLimitConfig) o;
        return requestsPerWindow == that.requestsPerWindow &&
               Objects.equals(windowDuration, that.windowDuration);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(requestsPerWindow, windowDuration);
    }
    
    @Override
    public String toString() {
        return "RateLimitConfig{" +
               "requestsPerWindow=" + requestsPerWindow +
               ", windowDuration=" + windowDuration +
               '}';
    }
}
