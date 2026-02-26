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

@DisplayName("CollectionConfigEventPublisher")
class CollectionConfigEventPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private CollectionConfigEventPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        publisher = new CollectionConfigEventPublisher(kafkaTemplate, objectMapper);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("Should target 'collections' collection")
    void shouldTargetCollections() {
        assertEquals("collections", publisher.getCollectionName());
    }

    @Test
    @DisplayName("Should have order 100 to run after schema hooks")
    void shouldHaveOrderAfterSchemaHooks() {
        assertEquals(100, publisher.getOrder());
    }

    @Test
    @DisplayName("Should publish CREATED event after create")
    void shouldPublishCreatedEvent() throws Exception {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "col-1",
            "name", "orders",
            "displayName", "Orders",
            "active", true,
            "currentVersion", 1
        ));

        publisher.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
            eq(CollectionConfigEventPublisher.TOPIC),
            eq("col-1"),
            payloadCaptor.capture()
        );

        CollectionChangedPayload payload = objectMapper.readValue(
            payloadCaptor.getValue(), CollectionChangedPayload.class);
        assertEquals("col-1", payload.getId());
        assertEquals("orders", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
        assertTrue(payload.isActive());
        assertEquals(1, payload.getCurrentVersion());
    }

    @Test
    @DisplayName("Should publish UPDATED event after update")
    void shouldPublishUpdatedEvent() throws Exception {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "col-1",
            "name", "orders",
            "active", true
        ));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "col-1",
            "name", "orders"
        ));

        publisher.afterUpdate("col-1", record, previous, "tenant-1");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
            eq(CollectionConfigEventPublisher.TOPIC),
            eq("col-1"),
            payloadCaptor.capture()
        );

        CollectionChangedPayload payload = objectMapper.readValue(
            payloadCaptor.getValue(), CollectionChangedPayload.class);
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
    }

    @Test
    @DisplayName("Should publish DELETED event after delete")
    void shouldPublishDeletedEvent() throws Exception {
        publisher.afterDelete("col-1", "tenant-1");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
            eq(CollectionConfigEventPublisher.TOPIC),
            eq("col-1"),
            payloadCaptor.capture()
        );

        CollectionChangedPayload payload = objectMapper.readValue(
            payloadCaptor.getValue(), CollectionChangedPayload.class);
        assertEquals("col-1", payload.getId());
        assertEquals(ChangeType.DELETED, payload.getChangeType());
    }
}
