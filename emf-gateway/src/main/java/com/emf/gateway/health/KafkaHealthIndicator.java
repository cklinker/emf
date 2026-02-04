package com.emf.gateway.health;

import com.emf.runtime.event.ConfigEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Custom health indicator for Kafka consumer connectivity.
 * 
 * This indicator checks if the Kafka consumer can connect to the Kafka cluster
 * by attempting to list topics. It provides detailed information about the
 * connection status and any errors encountered.
 * 
 * Validates: Requirements 12.3
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    
    private final ConsumerFactory<String, ConfigEvent<?>> consumerFactory;
    
    public KafkaHealthIndicator(ConsumerFactory<String, ConfigEvent<?>> consumerFactory) {
        this.consumerFactory = consumerFactory;
    }
    
    @Override
    public Health health() {
        Consumer<String, ConfigEvent<?>> consumer = null;
        try {
            // Create a temporary consumer to check connectivity
            consumer = consumerFactory.createConsumer("health-check", "health-check");
            
            // Attempt to list topics with timeout
            Map<String, ?> topics = consumer.listTopics(HEALTH_CHECK_TIMEOUT);
            
            log.debug("Kafka health check passed, found {} topics", topics.size());
            return Health.up()
                .withDetail("connection", "active")
                .withDetail("topicCount", topics.size())
                .build();
            
        } catch (Exception e) {
            log.error("Kafka health check failed with exception", e);
            return Health.down()
                .withDetail("connection", "failed")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        } finally {
            if (consumer != null) {
                try {
                    consumer.close(Duration.ofSeconds(1));
                } catch (Exception e) {
                    log.warn("Failed to close health check consumer", e);
                }
            }
        }
    }
}
