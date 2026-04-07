package io.kelta.worker.listener;

import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("FieldConfigEventPublisher")
class FieldConfigEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private FieldConfigEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        publisher = new FieldConfigEventPublisher(eventPublisher);
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
    @DisplayName("Should publish PlatformEvent-wrapped collection UPDATED event after field create")
    void shouldPublishOnFieldCreate() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "type", "string",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should publish PlatformEvent-wrapped event after field update")
    void shouldPublishOnFieldUpdate() {
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

        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should not publish when collectionId is missing")
    void shouldNotPublishWhenCollectionIdMissing() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should not publish on field delete (no collection context)")
    void shouldNotPublishOnFieldDelete() {
        publisher.afterDelete("field-1", "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
    }
}
