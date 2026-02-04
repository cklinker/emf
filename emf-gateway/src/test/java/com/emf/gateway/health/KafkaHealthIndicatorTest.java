package com.emf.gateway.health;

import com.emf.runtime.event.ConfigEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.ConsumerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaHealthIndicator.
 * 
 * Tests verify that the health indicator correctly reports Kafka connectivity status.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaHealthIndicator Tests")
class KafkaHealthIndicatorTest {
    
    @Mock
    private ConsumerFactory<String, ConfigEvent<?>> consumerFactory;
    
    @Mock
    private Consumer<String, ConfigEvent<?>> consumer;
    
    private KafkaHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new KafkaHealthIndicator(consumerFactory);
    }
    
    @Test
    @DisplayName("Should report UP when Kafka is reachable")
    void shouldReportUpWhenKafkaIsReachable() {
        // Given
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        topics.put("topic1", new ArrayList<>());
        topics.put("topic2", new ArrayList<>());
        topics.put("topic3", new ArrayList<>());
        
        when(consumerFactory.createConsumer(anyString(), anyString())).thenReturn(consumer);
        when(consumer.listTopics(any(Duration.class))).thenReturn(topics);
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
        assertThat(health.getDetails()).containsEntry("topicCount", 3);
        
        verify(consumerFactory).createConsumer("health-check", "health-check");
        verify(consumer).listTopics(any(Duration.class));
        verify(consumer).close(any(Duration.class));
    }
    
    @Test
    @DisplayName("Should report UP when Kafka has no topics")
    void shouldReportUpWhenKafkaHasNoTopics() {
        // Given
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        
        when(consumerFactory.createConsumer(anyString(), anyString())).thenReturn(consumer);
        when(consumer.listTopics(any(Duration.class))).thenReturn(topics);
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
        assertThat(health.getDetails()).containsEntry("topicCount", 0);
    }
    
    @Test
    @DisplayName("Should report DOWN when consumer creation fails")
    void shouldReportDownWhenConsumerCreationFails() {
        // Given
        when(consumerFactory.createConsumer(anyString(), anyString()))
            .thenThrow(new RuntimeException("Failed to create consumer"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Failed to create consumer");
    }
    
    @Test
    @DisplayName("Should report DOWN when listing topics fails")
    void shouldReportDownWhenListingTopicsFails() {
        // Given
        when(consumerFactory.createConsumer(anyString(), anyString())).thenReturn(consumer);
        when(consumer.listTopics(any(Duration.class)))
            .thenThrow(new RuntimeException("Connection timeout"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Connection timeout");
        
        verify(consumer).close(any(Duration.class));
    }
    
    @Test
    @DisplayName("Should close consumer even when close fails")
    void shouldCloseConsumerEvenWhenCloseFails() {
        // Given
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        topics.put("topic1", new ArrayList<>());
        
        when(consumerFactory.createConsumer(anyString(), anyString())).thenReturn(consumer);
        when(consumer.listTopics(any(Duration.class))).thenReturn(topics);
        doThrow(new RuntimeException("Close failed")).when(consumer).close(any(Duration.class));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verify(consumer).close(any(Duration.class));
    }
}
