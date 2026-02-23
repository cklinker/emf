package com.emf.runtime.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RecordChangeEvent}.
 */
class RecordChangeEventTest {

    @Test
    @DisplayName("Should create CREATED event via factory method")
    void shouldCreateCreatedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "Test");

        RecordChangeEvent event = RecordChangeEvent.created(
                "tenant-1", "products", "rec-1", data, "user-1");

        assertNotNull(event.getEventId());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals("products", event.getCollectionName());
        assertEquals("rec-1", event.getRecordId());
        assertEquals(ChangeType.CREATED, event.getChangeType());
        assertEquals(data, event.getData());
        assertNull(event.getPreviousData());
        assertTrue(event.getChangedFields().isEmpty());
        assertEquals("user-1", event.getUserId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("Should create UPDATED event via factory method")
    void shouldCreateUpdatedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "New Name");
        Map<String, Object> previousData = Map.of("id", "rec-1", "name", "Old Name");
        List<String> changedFields = List.of("name");

        RecordChangeEvent event = RecordChangeEvent.updated(
                "tenant-1", "products", "rec-1", data, previousData,
                changedFields, "user-1");

        assertEquals(ChangeType.UPDATED, event.getChangeType());
        assertEquals(data, event.getData());
        assertEquals(previousData, event.getPreviousData());
        assertEquals(changedFields, event.getChangedFields());
    }

    @Test
    @DisplayName("Should create DELETED event via factory method")
    void shouldCreateDeletedEvent() {
        Map<String, Object> data = Map.of("id", "rec-1", "name", "Test");

        RecordChangeEvent event = RecordChangeEvent.deleted(
                "tenant-1", "products", "rec-1", data, "user-1");

        assertEquals(ChangeType.DELETED, event.getChangeType());
        assertEquals(data, event.getData());
        assertNull(event.getPreviousData());
        assertTrue(event.getChangedFields().isEmpty());
    }

    @Test
    @DisplayName("Should generate unique event IDs")
    void shouldGenerateUniqueEventIds() {
        Map<String, Object> data = Map.of("id", "rec-1");

        RecordChangeEvent event1 = RecordChangeEvent.created(
                "t", "c", "r", data, "u");
        RecordChangeEvent event2 = RecordChangeEvent.created(
                "t", "c", "r", data, "u");

        assertNotEquals(event1.getEventId(), event2.getEventId());
    }

    @Test
    @DisplayName("Should compare equality by eventId")
    void shouldCompareEqualityByEventId() {
        RecordChangeEvent event1 = new RecordChangeEvent(
                "evt-1", "t", "c", "r", ChangeType.CREATED,
                Map.of(), null, List.of(), "u", java.time.Instant.now());
        RecordChangeEvent event2 = new RecordChangeEvent(
                "evt-1", "other-t", "other-c", "other-r", ChangeType.DELETED,
                Map.of(), null, List.of(), "other-u", java.time.Instant.now());
        RecordChangeEvent event3 = new RecordChangeEvent(
                "evt-2", "t", "c", "r", ChangeType.CREATED,
                Map.of(), null, List.of(), "u", java.time.Instant.now());

        assertEquals(event1, event2, "Events with same eventId should be equal");
        assertNotEquals(event1, event3, "Events with different eventIds should not be equal");
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    @DisplayName("Should support no-arg constructor for deserialization")
    void shouldSupportNoArgConstructor() {
        RecordChangeEvent event = new RecordChangeEvent();
        assertNull(event.getEventId());
        assertNull(event.getTenantId());
        assertNull(event.getChangeType());
    }

    @Test
    @DisplayName("Should support setters for deserialization")
    void shouldSupportSetters() {
        RecordChangeEvent event = new RecordChangeEvent();
        event.setEventId("evt-1");
        event.setTenantId("tenant-1");
        event.setCollectionName("products");
        event.setRecordId("rec-1");
        event.setChangeType(ChangeType.CREATED);
        event.setData(Map.of("name", "Test"));
        event.setPreviousData(null);
        event.setChangedFields(List.of());
        event.setUserId("user-1");
        event.setTimestamp(java.time.Instant.now());

        assertEquals("evt-1", event.getEventId());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals("products", event.getCollectionName());
        assertEquals("rec-1", event.getRecordId());
        assertEquals(ChangeType.CREATED, event.getChangeType());
        assertEquals("Test", event.getData().get("name"));
    }

    @Test
    @DisplayName("Should produce readable toString")
    void shouldProduceReadableToString() {
        RecordChangeEvent event = RecordChangeEvent.created(
                "tenant-1", "products", "rec-1",
                Map.of("id", "rec-1"), "user-1");

        String str = event.toString();
        assertTrue(str.contains("RecordChangeEvent"));
        assertTrue(str.contains("tenant-1"));
        assertTrue(str.contains("products"));
        assertTrue(str.contains("rec-1"));
        assertTrue(str.contains("CREATED"));
    }
}
