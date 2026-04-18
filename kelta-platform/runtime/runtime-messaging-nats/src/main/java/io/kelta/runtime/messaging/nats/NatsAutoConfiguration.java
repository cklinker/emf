package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.PlatformEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration for NATS JetStream messaging.
 *
 * <p>Provides connection management, event publishing, subscription management,
 * stream initialization, and health checking beans.
 *
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NatsProperties.class)
public class NatsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NatsConnectionManager natsConnectionManager(NatsProperties properties) throws Exception {
        return new NatsConnectionManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformEventPublisher.class)
    public NatsEventPublisher natsEventPublisher(NatsConnectionManager connectionManager,
                                                  ObjectMapper objectMapper) {
        return new NatsEventPublisher(connectionManager, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NatsSubscriptionManager natsSubscriptionManager(NatsConnectionManager connectionManager,
                                                            ObjectMapper objectMapper) {
        return new NatsSubscriptionManager(connectionManager, objectMapper);
    }

    @Bean
    public JetStreamInitializer jetStreamInitializer(NatsConnectionManager connectionManager) {
        return new JetStreamInitializer(connectionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public NatsHealthIndicator natsHealthIndicator(NatsConnectionManager connectionManager) {
        return new NatsHealthIndicator(connectionManager);
    }
}
