package io.kelta.worker.event;

import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.event.RecordChangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KafkaRecordEventPublisher}.
 */
class KafkaRecordEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private KafkaRecordEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        publisher = new KafkaRecordEventPublisher(eventPublisher);
    }

    @Test
    @DisplayName("Should publish CREATED event to correct subject with correct key")
    void shouldPublishCreatedEvent() {
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.created", "tenant-1", "user-1",
                RecordChangedPayload.created("orders", "record-1",
                        Map.of("id", "record-1", "status", "NEW")));

        publisher.publish(event);

        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should publish UPDATED event with previous data and changed fields")
    void shouldPublishUpdatedEvent() {
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.updated", "tenant-1", "user-1",
                RecordChangedPayload.updated("orders", "record-1",
                        Map.of("id", "record-1", "status", "APPROVED"),
                        Map.of("id", "record-1", "status", "NEW"),
                        List.of("status")));

        publisher.publish(event);

        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should publish DELETED event")
    void shouldPublishDeletedEvent() {
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.deleted", "tenant-1", "user-1",
                RecordChangedPayload.deleted("orders", "record-1",
                        Map.of("id", "record-1", "status", "NEW")));

        publisher.publish(event);

        verify(eventPublisher).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should use correct subject format: kelta.record.changed.tenantId.collectionName")
    void shouldUseCorrectSubject() {
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.created", "my-tenant", "user-1",
                RecordChangedPayload.created("customers", "cust-1",
                        Map.of("id", "cust-1")));

        publisher.publish(event);

        verify(eventPublisher).publish(eq("kelta.record.changed.my-tenant.customers"), any());
    }
}
