package io.kelta.worker.repository;

import java.time.Instant;

/**
 * A member's standing interest in a target, read from {@code watch}.
 *
 * <p>{@code criteria} and {@code channels} carry the raw JSON text of their JSONB
 * columns — {@code WatchCriteria.parse} turns the former into a usable predicate.
 */
public record Watch(
        String id,
        String tenantId,
        String memberId,
        String targetId,
        String criteria,
        String channels,
        String status,
        Instant expiresAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_FULFILLED = "FULFILLED";

    /**
     * True when this watch should be considered for alerting at {@code now}.
     * Expiry is judged here rather than trusted from {@code status}, so a watch
     * whose window closed is never alerted on just because a sweep is late.
     */
    public boolean isLive(Instant now) {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
