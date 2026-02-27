package com.emf.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka configuration for the gateway.
 *
 * <p>Enables Spring Kafka listener infrastructure. Consumer factories and
 * listener container factories are defined in {@link RecordEventKafkaConfig},
 * which uses {@code StringDeserializer} to match the worker's
 * {@code StringSerializer} serialization format.
 */
@Configuration
@EnableKafka
public class KafkaConfig {
}
