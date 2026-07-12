package io.kelta.runtime.storage;

/**
 * Thrown when a delete is rejected by a Postgres foreign-key constraint
 * (SQL state 23503): other records still reference the target row.
 *
 * <p>Maps to HTTP 409 Conflict in {@code GlobalExceptionHandler} — before this
 * existed, every restricting FK surfaced as an opaque 500 {@link StorageException}.
 *
 * @since 1.0.0
 */
public class ReferencedRecordConflictException extends StorageException {

    private final String collectionName;
    private final String recordId;

    public ReferencedRecordConflictException(String collectionName, String recordId, Throwable cause) {
        super("Record '" + recordId + "' in collection '" + collectionName
                + "' is still referenced by other records and cannot be deleted", cause);
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
