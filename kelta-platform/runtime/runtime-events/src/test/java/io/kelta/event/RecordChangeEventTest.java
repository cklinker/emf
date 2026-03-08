package io.kelta.runtime.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlatformEvent} with {@link RecordChangedPayload}.
 *
 * Replaces the previous RecordChangeEvent tests after the event system unification.
 */
class RecordChangeEventTest {

    @Test
    @DisplayName("Should create CREATED event via EventFactory")
    void shouldCreateCreatedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "Test");

        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.created", "tenant-1", "user-1",
                RecordChangedPayload.created("products", "rec-1", data));

        assertNotNull(event.getEventId());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals("record.created", event.getEventType());
        assertEquals("user-1", event.getUserId());
        assertNotNull(event.getTimestamp());

        RecordChangedPayload payload = event.getPayload();
        assertEquals("products", payload.getCollectionName());
        assertEquals("rec-1", payload.getRecordId());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
        assertEquals(data, payload.getData());
        assertNull(payload.getPreviousData());
        assertTrue(payload.getChangedFields().isEmpty());
    }

    @Test
    @DisplayName("Should create UPDATED event via EventFactory")
    void shouldCreateUpdatedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "New Name");
        Map<String, Object> previousData = Map.of("id", "rec-1", "name", "Old Name");
        List<String> changedFields = List.of("name");

        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.updated", "tenant-1", "user-1",
                RecordChangedPayload.updated("products", "rec-1", data, previousData, changedFields));

        RecordChangedPayload payload = event.getPayload();
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
        assertEquals(data, payload.getData());
        assertEquals(previousData, payload.getPreviousData());
        assertEquals(changedFields, payload.getChangedFields());
    }

    @Test
    @DisplayName("Should create DELETED event via EventFactory")
    void shouldCreateDeletedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "Test");

        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.deleted", "tenant-1", "user-1",
                RecordChangedPayload.deleted("products", "rec-1", data));

        RecordChangedPayload payload = event.getPayload();
        assertEquals(ChangeType.DELETED, payload.getChangeType());
        assertEquals(data, payload.getData());
        assertNull(payload.getPreviousData());
        assertTrue(payload.getChangedFields().isEmpty());
    }

    @Test
    @DisplayName("Should generate unique event IDs")
    void shouldGenerateUniqueEventIds() {
        Map<String, Object> data = Map.of("id", "rec-1");

        PlatformEvent<RecordChangedPayload> event1 = EventFactory.createRecordEvent(
                "record.created", "t", "u",
                RecordChangedPayload.created("c", "r", data));
        PlatformEvent<RecordChangedPayload> event2 = EventFactory.createRecordEvent(
                "record.created", "t", "u",
                RecordChangedPayload.created("c", "r", data));

        assertNotEquals(event1.getEventId(), event2.getEventId());
    }

    @Test
    @DisplayName("Should compare equality by eventId")
    void shouldCompareEqualityByEventId() {
        Instant now = Instant.now();
        RecordChangedPayload payload1 = RecordChangedPayload.created("c", "r", Map.of());
        RecordChangedPayload payload2 = RecordChangedPayload.deleted("other-c", "other-r", Map.of());

        PlatformEvent<RecordChangedPayload> event1 = new PlatformEvent<>(
                "evt-1", "record.created", "t", null, "u", now, payload1);
        PlatformEvent<RecordChangedPayload> event2 = new PlatformEvent<>(
                "evt-1", "record.deleted", "other-t", null, "other-u", now, payload2);
        PlatformEvent<RecordChangedPayload> event3 = new PlatformEvent<>(
                "evt-2", "record.created", "t", null, "u", now, payload1);

        assertEquals(event1, event2, "Events with same eventId should be equal");
        assertNotEquals(event1, event3, "Events with different eventIds should not be equal");
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    @DisplayName("Should support no-arg constructor for deserialization")
    void shouldSupportNoArgConstructor() {
        PlatformEvent<RecordChangedPayload> event = new PlatformEvent<>();
        assertNull(event.getEventId());
        assertNull(event.getTenantId());
        assertNull(event.getPayload());
    }

    @Test
    @DisplayName("Should support setters for deserialization")
    void shouldSupportSetters() {
        PlatformEvent<RecordChangedPayload> event = new PlatformEvent<>();
        event.setEventId("evt-1");
        event.setEventType("record.created");
        event.setTenantId("tenant-1");
        event.setCorrelationId("corr-1");
        event.setUserId("user-1");
        event.setTimestamp(Instant.now());

        RecordChangedPayload payload = RecordChangedPayload.created("products", "rec-1", Map.of("name", "Test"));
        event.setPayload(payload);

        assertEquals("evt-1", event.getEventId());
        assertEquals("record.created", event.getEventType());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals("corr-1", event.getCorrelationId());
        assertEquals("user-1", event.getUserId());
        assertEquals("products", event.getPayload().getCollectionName());
        assertEquals("rec-1", event.getPayload().getRecordId());
        assertEquals(ChangeType.CREATED, event.getPayload().getChangeType());
        assertEquals("Test", event.getPayload().getData().get("name"));
    }

    @Test
    @DisplayName("Should produce readable toString")
    void shouldProduceReadableToString() {
        PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                "record.created", "tenant-1", "user-1",
                RecordChangedPayload.created("products", "rec-1", Map.of("id", "rec-1")));

        String str = event.toString();
        assertTrue(str.contains("PlatformEvent"));
        assertTrue(str.contains("tenant-1"));
        assertTrue(str.contains("record.created"));
    }

    @Test
    @DisplayName("RecordChangedPayload static factory methods should set correct change types")
    void recordChangedPayloadFactoryMethods() {
        RecordChangedPayload created = RecordChangedPayload.created("col", "r1", Map.of());
        assertEquals(ChangeType.CREATED, created.getChangeType());
        assertNull(created.getPreviousData());
        assertTrue(created.getChangedFields().isEmpty());

        RecordChangedPayload updated = RecordChangedPayload.updated(
                "col", "r1", Map.of("a", 1), Map.of("a", 0), List.of("a"));
        assertEquals(ChangeType.UPDATED, updated.getChangeType());
        assertNotNull(updated.getPreviousData());
        assertEquals(List.of("a"), updated.getChangedFields());

        RecordChangedPayload deleted = RecordChangedPayload.deleted("col", "r1", Map.of());
        assertEquals(ChangeType.DELETED, deleted.getChangeType());
        assertNull(deleted.getPreviousData());
        assertTrue(deleted.getChangedFields().isEmpty());
    }

    @Test
    @DisplayName("RecordChangedPayload should support no-arg constructor and setters")
    void recordChangedPayloadSetters() {
        RecordChangedPayload payload = new RecordChangedPayload();
        payload.setCollectionName("products");
        payload.setRecordId("rec-1");
        payload.setChangeType(ChangeType.CREATED);
        payload.setData(Map.of("name", "Test"));
        payload.setPreviousData(null);
        payload.setChangedFields(List.of());

        assertEquals("products", payload.getCollectionName());
        assertEquals("rec-1", payload.getRecordId());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    @DisplayName("RecordChangedPayload equality should be based on collectionName, recordId, changeType")
    void recordChangedPayloadEquality() {
        RecordChangedPayload p1 = RecordChangedPayload.created("col", "r1", Map.of("a", 1));
        RecordChangedPayload p2 = RecordChangedPayload.created("col", "r1", Map.of("b", 2));
        RecordChangedPayload p3 = RecordChangedPayload.deleted("col", "r1", Map.of("a", 1));

        assertEquals(p1, p2, "Same collectionName, recordId, changeType should be equal");
        assertNotEquals(p1, p3, "Different changeType should not be equal");
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
