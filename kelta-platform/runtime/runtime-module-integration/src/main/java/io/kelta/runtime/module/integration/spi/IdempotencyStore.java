package io.kelta.runtime.module.integration.spi;

import java.time.Duration;
import java.util.Optional;

/**
 * Per-tenant idempotency cache for non-idempotent API calls. The
 * {@code CALL_API} handler uses this to short-circuit duplicate executions
 * (e.g., a flow retried after a partial failure) so we never double-charge,
 * double-create, or otherwise duplicate side-effects on remote systems.
 */
public interface IdempotencyStore {

    /** Looks up a cached response for {@code key}. */
    Optional<CachedResponse> lookup(String tenantId, String key);

    /** Records a response for {@code key} with the given TTL. */
    void record(String tenantId, String key, String flowRunId, String stateName,
                int statusCode, String responseBody, Duration ttl);

    /**
     * Cached response — what the upstream replied last time, replayed when a
     * subsequent call presents the same {@code Idempotency-Key}.
     */
    record CachedResponse(int statusCode, String responseBody, String responseHash) {
    }
}
