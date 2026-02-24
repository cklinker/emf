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
 * Custom health indicator for Worker Service connectivity.
 *
 * <p>This indicator checks if the Worker Service is reachable by calling
 * the actuator health endpoint. It provides detailed information about
 * the connection status and any errors encountered.
 */
@Component
public class WorkerHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(WorkerHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final String HEALTH_PATH = "/actuator/health";

    private final WebClient webClient;
    private final String workerServiceUrl;

    public WorkerHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
        this.workerServiceUrl = workerServiceUrl;
    }

    @Override
    public Health health() {
        try {
            webClient.get()
                .uri(HEALTH_PATH)
                .retrieve()
                .toBodilessEntity()
                .timeout(HEALTH_CHECK_TIMEOUT)
                .block();

            log.debug("Worker health check passed");
            return Health.up()
                .withDetail("connection", "active")
                .withDetail("url", workerServiceUrl)
                .withDetail("endpoint", HEALTH_PATH)
                .build();

        } catch (Exception e) {
            log.error("Worker health check failed with exception", e);
            return Health.down()
                .withDetail("connection", "failed")
                .withDetail("url", workerServiceUrl)
                .withDetail("endpoint", HEALTH_PATH)
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
