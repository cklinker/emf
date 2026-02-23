package com.emf.runtime.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Event published when a record is created, updated, or deleted in a collection.
 *
 * <p>This is a shared event class used across all EMF services. It carries the full
 * record data for created/updated events and the previous data for updates, enabling
 * downstream consumers (workflow engine, audit, etc.) to react to data changes.
 *
 * <p>Published to the {@code emf.record.changed} Kafka topic, keyed by
 * {@code tenantId:collectionName} for partition ordering.
 *
 * @since 1.0.0
 */
public class RecordChangeEvent {

    private String eventId;
    private String tenantId;
    private String collectionName;
    private String recordId;
    private ChangeType changeType;
    private Map<String, Object> data;
    private Map<String, Object> previousData;
    private List<String> changedFields;
    private String userId;
    private Instant timestamp;

    /**
     * Default constructor for deserialization.
     */
    public RecordChangeEvent() {
    }

    /**
     * Creates a new RecordChangeEvent with all fields.
     */
    public RecordChangeEvent(String eventId, String tenantId, String collectionName,
                             String recordId, ChangeType changeType,
                             Map<String, Object> data, Map<String, Object> previousData,
                             List<String> changedFields, String userId, Instant timestamp) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.collectionName = collectionName;
        this.recordId = recordId;
        this.changeType = changeType;
        this.data = data;
        this.previousData = previousData;
        this.changedFields = changedFields;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    /**
     * Creates a CREATED event for a new record.
     */
    public static RecordChangeEvent created(String tenantId, String collectionName,
                                            String recordId, Map<String, Object> data,
                                            String userId) {
        return new RecordChangeEvent(
                UUID.randomUUID().toString(), tenantId, collectionName,
                recordId, ChangeType.CREATED, data, null, List.of(),
                userId, Instant.now());
    }

    /**
     * Creates an UPDATED event for a modified record.
     *
     * @param tenantId       the tenant ID
     * @param collectionName the collection name
     * @param recordId       the record ID
     * @param data           the updated record data (merged/full record)
     * @param previousData   the record data before the update
     * @param changedFields  list of field names that changed
     * @param userId         the user who made the change
     */
    public static RecordChangeEvent updated(String tenantId, String collectionName,
                                            String recordId, Map<String, Object> data,
                                            Map<String, Object> previousData,
                                            List<String> changedFields, String userId) {
        return new RecordChangeEvent(
                UUID.randomUUID().toString(), tenantId, collectionName,
                recordId, ChangeType.UPDATED, data, previousData, changedFields,
                userId, Instant.now());
    }

    /**
     * Creates a DELETED event for a removed record.
     */
    public static RecordChangeEvent deleted(String tenantId, String collectionName,
                                            String recordId, Map<String, Object> data,
                                            String userId) {
        return new RecordChangeEvent(
                UUID.randomUUID().toString(), tenantId, collectionName,
                recordId, ChangeType.DELETED, data, null, List.of(),
                userId, Instant.now());
    }

    // --- Getters and Setters ---

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public Map<String, Object> getPreviousData() { return previousData; }
    public void setPreviousData(Map<String, Object> previousData) { this.previousData = previousData; }

    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordChangeEvent that = (RecordChangeEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "RecordChangeEvent{" +
                "eventId='" + eventId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", recordId='" + recordId + '\'' +
                ", changeType=" + changeType +
                ", changedFields=" + changedFields +
                ", userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
