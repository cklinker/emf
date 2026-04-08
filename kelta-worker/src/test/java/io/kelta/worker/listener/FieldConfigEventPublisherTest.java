package io.kelta.worker.listener;

import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FieldConfigEventPublisher")
class FieldConfigEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private JdbcTemplate jdbcTemplate;
    private FieldConfigEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        publisher = new FieldConfigEventPublisher(eventPublisher, jdbcTemplate);
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
    @DisplayName("Should publish collection UPDATED event with resolved name after field create")
    void shouldPublishOnFieldCreate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "orders")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "type", "string",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> eventCaptor = captor();
        verify(eventPublisher).publish(eq("kelta.config.collection.changed.col-1"), eventCaptor.capture());

        CollectionChangedPayload payload = eventCaptor.getValue().getPayload();
        assertEquals("col-1", payload.getId());
        assertEquals("orders", payload.getName());
    }

    @Test
    @DisplayName("Should publish event with resolved name after field update")
    void shouldPublishOnFieldUpdate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "orders")));

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

        ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> eventCaptor = captor();
        verify(eventPublisher).publish(eq("kelta.config.collection.changed.col-1"), eventCaptor.capture());

        CollectionChangedPayload payload = eventCaptor.getValue().getPayload();
        assertEquals("col-1", payload.getId());
        assertEquals("orders", payload.getName());
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
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Should not publish when collection name cannot be resolved")
    void shouldNotPublishWhenCollectionNameCannotBeResolved() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-missing")))
                .thenReturn(List.of());

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-missing"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should not publish when resolveCollectionName throws")
    void shouldNotPublishWhenResolveCollectionNameThrows() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenThrow(new RuntimeException("db down"));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should not publish on field delete (no collection context)")
    void shouldNotPublishOnFieldDelete() {
        publisher.afterDelete("field-1", "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
        verifyNoInteractions(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> captor() {
        return ArgumentCaptor.forClass(PlatformEvent.class);
    }
}
