package com.emf.gateway.config;

import com.emf.runtime.event.ConfigEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for consuming configuration change events.
 *
 * This configuration sets up Kafka consumers to listen to three topics:
 * - Collection changed events
 * - Authorization changed events
 * - Service changed events
 *
 * The consumers use JSON deserialization to convert Kafka messages into ConfigEvent objects
 * with appropriate payload types. Malformed messages are logged and skipped via
 * {@link ErrorHandlingDeserializer} so that a single bad record does not block the consumer.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Creates the Kafka consumer factory with error-handling JSON deserialization.
     *
     * Configures the consumer to:
     * - Use ErrorHandlingDeserializer wrapping JsonDeserializer for values
     * - Trust the shared runtime-events package
     * - Use earliest offset reset strategy
     * - Skip and log malformed records instead of blocking the consumer
     *
     * @return ConsumerFactory for creating Kafka consumers
     */
    @Bean
    public ConsumerFactory<String, ConfigEvent<?>> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Use ErrorHandlingDeserializer to wrap JsonDeserializer.
        // This allows the consumer to skip malformed records (e.g., raw JSON without
        // ConfigEvent wrapper) instead of getting stuck in a retry loop.
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        // Configure the inner JsonDeserializer to trust the shared event package
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.emf.runtime.event,java.util");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), null);
    }

    /**
     * Creates the Kafka listener container factory with error handling.
     *
     * Uses a {@link DefaultErrorHandler} that logs and skips records that fail
     * deserialization or processing, ensuring the consumer advances past bad records.
     *
     * @return ConcurrentKafkaListenerContainerFactory for creating listener containers
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ConfigEvent<?>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ConfigEvent<?>> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Configure error handler to skip bad records after 0 retries (immediate skip).
        // This prevents malformed messages from blocking the consumer group.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> log.warn(
                "Skipping unprocessable Kafka record on topic={} partition={} offset={}: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage()
            ),
            new FixedBackOff(0L, 0L) // No retries — skip immediately
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
