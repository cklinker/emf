package com.emf.controlplane.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for health check indicators.
 * 
 * This configuration provides custom health indicators for:
 * - Kafka: Checks if the Kafka cluster is reachable
 * - Redis: Checks if Redis is reachable (uses Spring's built-in indicator)
 * - Database: Uses Spring's built-in DataSourceHealthIndicator
 * 
 * Health check endpoints:
 * - /actuator/health/liveness: Basic liveness check (application is running)
 * - /actuator/health/readiness: Readiness check including DB, Kafka, Redis
 * 
 * Requirements satisfied:
 * - 13.5: Provide liveness health check at /actuator/health/liveness
 * - 13.6: Provide readiness health check at /actuator/health/readiness
 * - 13.7: Report unhealthy when database is unavailable
 * - 13.8: Report unhealthy when Kafka is unavailable
 * - 13.9: Report unhealthy when Redis is unavailable
 */
@Configuration
public class HealthCheckConfig {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckConfig.class);

    /**
     * Custom Kafka health indicator that checks cluster connectivity.
     * This indicator verifies that the Kafka cluster is reachable and responsive.
     * 
     * @param kafkaAdmin The KafkaAdmin bean for cluster operations
     * @return HealthIndicator for Kafka
     * 
     * Validates: Requirement 13.8
     */
    @Bean
    @ConditionalOnClass(KafkaAdmin.class)
    @ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public HealthIndicator kafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        log.info("Configuring Kafka health indicator");
        
        return () -> {
            try {
                // Get admin client properties from KafkaAdmin
                AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                
                try {
                    // Try to describe the cluster with a timeout
                    DescribeClusterOptions options = new DescribeClusterOptions()
                            .timeoutMs(5000);
                    
                    String clusterId = adminClient.describeCluster(options)
                            .clusterId()
                            .get(5, TimeUnit.SECONDS);
                    
                    int nodeCount = adminClient.describeCluster(options)
                            .nodes()
                            .get(5, TimeUnit.SECONDS)
                            .size();
                    
                    return Health.up()
                            .withDetail("clusterId", clusterId)
                            .withDetail("nodeCount", nodeCount)
                            .build();
                            
                } finally {
                    adminClient.close();
                }
                
            } catch (Exception e) {
                log.warn("Kafka health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .withException(e)
                        .build();
            }
        };
    }

    /**
     * Custom Redis health indicator that checks connectivity.
     * This indicator verifies that Redis is reachable by executing a PING command.
     * 
     * Note: Spring Boot provides a built-in RedisHealthIndicator, but this custom
     * implementation provides more detailed information and better error handling.
     * 
     * @param connectionFactory The Redis connection factory
     * @return HealthIndicator for Redis
     * 
     * Validates: Requirement 13.9
     */
    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis health indicator");
        
        return () -> {
            try {
                // Try to get a connection and execute PING
                var connection = connectionFactory.getConnection();
                try {
                    String pong = connection.ping();
                    if ("PONG".equals(pong)) {
                        return Health.up()
                                .withDetail("response", pong)
                                .build();
                    } else {
                        return Health.down()
                                .withDetail("response", pong)
                                .withDetail("error", "Unexpected PING response")
                                .build();
                    }
                } finally {
                    connection.close();
                }
                
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .withException(e)
                        .build();
            }
        };
    }
}
