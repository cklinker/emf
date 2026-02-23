package com.emf.worker.event;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KafkaRecordEventPublisher}.
 */
class KafkaRecordEventPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private KafkaRecordEventPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for Instant serialization
        publisher = new KafkaRecordEventPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should publish CREATED event to correct topic with correct key")
    @SuppressWarnings("unchecked")
    void shouldPublishCreatedEvent() {
        RecordChangeEvent event = RecordChangeEvent.created(
                "tenant-1", "orders", "record-1",
                Map.of("id", "record-1", "status", "NEW"), "user-1");

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publish(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals(KafkaRecordEventPublisher.TOPIC, topicCaptor.getValue());
        assertEquals("tenant-1:orders", keyCaptor.getValue());

        // Verify the payload is valid JSON containing expected fields
        String payload = payloadCaptor.getValue();
        assertNotNull(payload);
        assertTrue(payload.contains("\"changeType\":\"CREATED\""));
        assertTrue(payload.contains("\"collectionName\":\"orders\""));
        assertTrue(payload.contains("\"recordId\":\"record-1\""));
    }

    @Test
    @DisplayName("Should publish UPDATED event with previous data and changed fields")
    @SuppressWarnings("unchecked")
    void shouldPublishUpdatedEvent() {
        RecordChangeEvent event = RecordChangeEvent.updated(
                "tenant-1", "orders", "record-1",
                Map.of("id", "record-1", "status", "APPROVED"),
                Map.of("id", "record-1", "status", "NEW"),
                java.util.List.of("status"), "user-1");

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(KafkaRecordEventPublisher.TOPIC),
                eq("tenant-1:orders"), anyString());
    }

    @Test
    @DisplayName("Should publish DELETED event")
    @SuppressWarnings("unchecked")
    void shouldPublishDeletedEvent() {
        RecordChangeEvent event = RecordChangeEvent.deleted(
                "tenant-1", "orders", "record-1",
                Map.of("id", "record-1", "status", "NEW"), "user-1");

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaRecordEventPublisher.TOPIC),
                eq("tenant-1:orders"), payloadCaptor.capture());

        assertTrue(payloadCaptor.getValue().contains("\"changeType\":\"DELETED\""));
    }

    @Test
    @DisplayName("Should not throw when Kafka send fails")
    @SuppressWarnings("unchecked")
    void shouldNotThrowWhenKafkaSendFails() {
        RecordChangeEvent event = RecordChangeEvent.created(
                "tenant-1", "orders", "record-1",
                Map.of("id", "record-1"), "user-1");

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka is down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        assertDoesNotThrow(() -> publisher.publish(event));
    }

    @Test
    @DisplayName("Should not throw when serialization fails")
    void shouldNotThrowWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        KafkaRecordEventPublisher failingPublisher =
                new KafkaRecordEventPublisher(kafkaTemplate, failingMapper);

        RecordChangeEvent event = RecordChangeEvent.created(
                "tenant-1", "orders", "record-1",
                Map.of("id", "record-1"), "user-1");

        assertDoesNotThrow(() -> failingPublisher.publish(event));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should use correct partition key format: tenantId:collectionName")
    @SuppressWarnings("unchecked")
    void shouldUseCorrectPartitionKey() {
        RecordChangeEvent event = RecordChangeEvent.created(
                "my-tenant", "customers", "cust-1",
                Map.of("id", "cust-1"), "user-1");

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(KafkaRecordEventPublisher.TOPIC),
                eq("my-tenant:customers"), anyString());
    }
}
