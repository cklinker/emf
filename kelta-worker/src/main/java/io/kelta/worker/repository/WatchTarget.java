package io.kelta.worker.repository;

/**
 * Something watchable, read from {@code watch_target}.
 *
 * <p>{@code metadata} carries the raw JSON text of the source-specific extras
 * column; the platform stores and returns it without interpreting it.
 */
public record WatchTarget(
        String id,
        String tenantId,
        String source,
        String externalId,
        String name,
        String category,
        String metadata,
        boolean active) {
}
