package com.emf.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Custom health indicator for Control Plane API connectivity.
 * 
 * This indicator checks if the Control Plane API is reachable by attempting
 * to call the bootstrap endpoint (which is public and doesn't require authentication).
 * It provides detailed information about the connection status and any errors encountered.
 * 
 * Validates: Requirements 12.4
 */
@Component
public class ControlPlaneHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(ControlPlaneHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    
    private final WebClient webClient;
    private final String controlPlaneUrl;
    private final String bootstrapPath;
    
    public ControlPlaneHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${emf.gateway.control-plane.url}") String controlPlaneUrl,
            @Value("${emf.gateway.control-plane.bootstrap-path}") String bootstrapPath) {
        // Use a plain WebClient without authentication since bootstrap endpoint is public
        this.webClient = webClientBuilder.baseUrl(controlPlaneUrl).build();
        this.controlPlaneUrl = controlPlaneUrl;
        this.bootstrapPath = bootstrapPath;
    }
    
    @Override
    public Health health() {
        try {
            // Attempt to call the bootstrap endpoint (public, no auth required)
            webClient.get()
                .uri(bootstrapPath)
                .retrieve()
                .toBodilessEntity()
                .timeout(HEALTH_CHECK_TIMEOUT)
                .block();
            
            log.debug("Control Plane health check passed");
            return Health.up()
                .withDetail("connection", "active")
                .withDetail("url", controlPlaneUrl)
                .withDetail("endpoint", bootstrapPath)
                .build();
            
        } catch (Exception e) {
            log.error("Control Plane health check failed with exception", e);
            return Health.down()
                .withDetail("connection", "failed")
                .withDetail("url", controlPlaneUrl)
                .withDetail("endpoint", bootstrapPath)
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
