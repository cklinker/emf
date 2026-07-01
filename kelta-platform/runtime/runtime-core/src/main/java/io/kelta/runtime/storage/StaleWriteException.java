package io.kelta.runtime.storage;

/**
 * Thrown when an optimistic-locking write is rejected because the record changed since the client
 * last read it. The client sent an {@code If-Match} ETag that no longer matches the record's
 * current version (derived from {@code updatedAt}). Maps to HTTP 409 Conflict.
 *
 * <p>Only raised when the client opts in by sending {@code If-Match}; absent the header the write
 * proceeds unchanged (back-compat).
 */
public class StaleWriteException extends RuntimeException {

    private final String collectionName;
    private final String recordId;

    public StaleWriteException(String collectionName, String recordId) {
        super("Record '" + recordId + "' in collection '" + collectionName
                + "' was modified since it was last read");
        this.collectionName = collectionName;
        this.recordId = recordId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getRecordId() {
        return recordId;
    }
}
