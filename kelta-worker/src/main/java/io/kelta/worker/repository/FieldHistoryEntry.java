package io.kelta.worker.repository;

import java.time.Instant;

/**
 * A single recorded change to a tracked field's value, read from {@code field_history}.
 *
 * <p>{@code oldValue}/{@code newValue} carry the raw JSON text stored in the JSONB columns
 * (e.g. {@code "\"active\""}, {@code "42"}, {@code null}); the controller re-parses them into
 * JSON:API attribute values so the wire type matches the field's own type.
 */
public record FieldHistoryEntry(
        String id,
        String collectionId,
        String recordId,
        String fieldName,
        String oldValue,
        String newValue,
        String changedBy,
        Instant changedAt,
        String changeSource) {
}
