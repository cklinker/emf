package com.emf.gateway.config;

import com.emf.runtime.event.ConfigEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for consuming configuration change events from the control plane.
 * 
 * This configuration sets up Kafka consumers to listen to three topics:
 * - Collection changed events
 * - Authorization changed events
 * - Service changed events
 * 
 * The consumers use JSON deserialization to convert Kafka messages into ConfigEvent objects
 * with appropriate payload types.
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    /**
     * Creates the Kafka consumer factory with JSON deserialization.
     * 
     * Configures the consumer to:
     * - Use JSON deserialization for values
     * - Trust the shared runtime-events package
     * - Use earliest offset reset strategy
     * 
     * @return ConsumerFactory for creating Kafka consumers
     */
    @Bean
    public ConsumerFactory<String, ConfigEvent<?>> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Configure JSON deserializer to trust the shared event package and java.util for Map payloads
        JsonDeserializer<ConfigEvent<?>> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.emf.runtime.event", "java.util");
        
        return new DefaultKafkaConsumerFactory<>(
            config,
            new StringDeserializer(),
            deserializer
        );
    }
    
    /**
     * Creates the Kafka listener container factory.
     * 
     * This factory is used by @KafkaListener annotations to create listener containers
     * that consume messages from Kafka topics.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for creating listener containers
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ConfigEvent<?>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ConfigEvent<?>> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
