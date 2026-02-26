package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FieldConfigEventPublisher")
class FieldConfigEventPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private FieldConfigEventPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        publisher = new FieldConfigEventPublisher(kafkaTemplate, objectMapper);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("Should target 'fields' collection")
    void shouldTargetFields() {
        assertEquals("fields", publisher.getCollectionName());
    }

    @Test
    @DisplayName("Should have order 100 to run after schema hooks")
    void shouldHaveOrderAfterSchemaHooks() {
        assertEquals(100, publisher.getOrder());
    }

    @Test
    @DisplayName("Should publish collection UPDATED event after field create")
    void shouldPublishOnFieldCreate() throws Exception {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "type", "string",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
            eq(FieldConfigEventPublisher.TOPIC),
            eq("col-1"),
            payloadCaptor.capture()
        );

        CollectionChangedPayload payload = objectMapper.readValue(
            payloadCaptor.getValue(), CollectionChangedPayload.class);
        assertEquals("col-1", payload.getId());
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
    }

    @Test
    @DisplayName("Should publish collection UPDATED event after field update")
    void shouldPublishOnFieldUpdate() throws Exception {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        verify(kafkaTemplate).send(
            eq(FieldConfigEventPublisher.TOPIC),
            eq("col-1"),
            anyString()
        );
    }

    @Test
    @DisplayName("Should not publish when collectionId is missing")
    void shouldNotPublishWhenCollectionIdMissing() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should not publish on field delete (no collection context)")
    void shouldNotPublishOnFieldDelete() {
        publisher.afterDelete("field-1", "tenant-1");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
