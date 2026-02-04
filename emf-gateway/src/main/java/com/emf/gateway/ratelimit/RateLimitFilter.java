package com.emf.gateway.ratelimit;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.route.RateLimitConfig;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

/**
 * Global filter that enforces rate limiting on routes.
 * Runs after authentication (order -50, after -100) but before routing decisions.
 * 
 * This filter:
 * - Extracts the route ID and principal from the request context
 * - Checks if the route has rate limiting configured
 * - Calls RedisRateLimiter to check if the request is allowed
 * - Returns 429 Too Many Requests if the rate limit is exceeded
 * - Adds rate limit headers to the response (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset)
 * - Includes Retry-After header when rate limit is exceeded
 * 
 * Validates: Requirements 8.2, 8.4, 8.5
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    
    private final RouteRegistry routeRegistry;
    private final RedisRateLimiter rateLimiter;
    
    /**
     * Creates a new RateLimitFilter.
     *
     * @param routeRegistry the route registry for looking up route definitions
     * @param rateLimiter the Redis-based rate limiter
     */
    public RateLimitFilter(RouteRegistry routeRegistry, RedisRateLimiter rateLimiter) {
        this.routeRegistry = routeRegistry;
        this.rateLimiter = rateLimiter;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get the authenticated principal (set by JwtAuthenticationFilter)
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        
        // If no principal, skip rate limiting (authentication filter will handle this)
        if (principal == null) {
            log.debug("No principal found, skipping rate limiting for path: {}", 
                exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }
        
        // Extract request details
        String path = exchange.getRequest().getPath().value();
        
        // Find the matching route to get rate limit config
        Optional<RouteDefinition> routeOpt = routeRegistry.findByPath(path);
        
        if (routeOpt.isEmpty()) {
            // No route found - skip rate limiting
            log.debug("No route found for path: {}, skipping rate limiting", path);
            return chain.filter(exchange);
        }
        
        RouteDefinition route = routeOpt.get();
        
        // Check if route has rate limiting configured
        if (!route.hasRateLimit()) {
            log.debug("No rate limit configured for route: {}, allowing request", route.getId());
            return chain.filter(exchange);
        }
        
        RateLimitConfig config = route.getRateLimit();
        String routeId = route.getId();
        String principalName = principal.getUsername();
        
        log.debug("Checking rate limit for route: {}, principal: {}, limit: {} requests per {}",
            routeId, principalName, config.getRequestsPerWindow(), config.getWindowDuration());
        
        // Check rate limit
        return rateLimiter.checkRateLimit(routeId, principalName, config)
            .flatMap(result -> {
                if (result.isAllowed()) {
                    // Request allowed - add rate limit headers and continue
                    log.debug("Rate limit check passed for route: {}, principal: {}, remaining: {}",
                        routeId, principalName, result.getRemainingRequests());
                    
                    addRateLimitHeaders(exchange, config, result);
                    return chain.filter(exchange);
                } else {
                    // Rate limit exceeded - return 429
                    log.warn("Rate limit exceeded for route: {}, principal: {}, retry after: {}",
                        routeId, principalName, result.getRetryAfter());
                    
                    return tooManyRequests(exchange, config, result);
                }
            });
    }
    
    /**
     * Adds rate limit headers to the response.
     *
     * @param exchange the server web exchange
     * @param config the rate limit configuration
     * @param result the rate limit check result
     */
    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimitConfig config, RateLimitResult result) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        
        // X-RateLimit-Limit: Maximum requests per window
        headers.add("X-RateLimit-Limit", String.valueOf(config.getRequestsPerWindow()));
        
        // X-RateLimit-Remaining: Remaining requests in current window
        headers.add("X-RateLimit-Remaining", String.valueOf(result.getRemainingRequests()));
        
        // X-RateLimit-Reset: Timestamp when window resets (current time + window duration)
        long resetTimestamp = Instant.now().plus(config.getWindowDuration()).getEpochSecond();
        headers.add("X-RateLimit-Reset", String.valueOf(resetTimestamp));
    }
    
    /**
     * Returns a 429 Too Many Requests response with rate limit information.
     *
     * @param exchange the server web exchange
     * @param config the rate limit configuration
     * @param result the rate limit check result
     * @return a Mono that completes the response
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitConfig config, RateLimitResult result) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        // Add rate limit headers
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(config.getRequestsPerWindow()));
        headers.add("X-RateLimit-Remaining", "0");
        
        long resetTimestamp = Instant.now().plus(config.getWindowDuration()).getEpochSecond();
        headers.add("X-RateLimit-Reset", String.valueOf(resetTimestamp));
        
        // Add Retry-After header (in seconds)
        long retryAfterSeconds = result.getRetryAfter().getSeconds();
        headers.add("Retry-After", String.valueOf(retryAfterSeconds));
        
        String errorJson = String.format(
            "{\"error\":{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Retry after %d seconds.\",\"path\":\"%s\"}}",
            retryAfterSeconds,
            exchange.getRequest().getPath().value()
        );
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }
    
    @Override
    public int getOrder() {
        return -50; // Run after authentication (-100) but before routing (0)
    }
}
