package io.kelta.worker.service.availability;

import java.time.Instant;
import java.util.Map;

/**
 * One availability observation reported by an external poller.
 *
 * <p>This is the wire contract published to
 * {@code kelta.availability.event.<tenantId>.<source>} (parent spec, Shared
 * contracts). Pollers live outside this repository, so <b>the field names here
 * are an external interface</b> — renaming one silently breaks every poller.
 *
 * <p>The event says what the poller <em>saw</em>, not what changed. Deciding
 * whether an observation is a genuine CLOSED→OPEN transition (and therefore worth
 * alerting on) is the matcher's job in slice 4, against {@code availability_state}.
 * That split is deliberate: pollers stay dumb and replaceable, and re-reporting
 * the same open slot every minute is harmless.
 *
 * @param source           poller-defined source key; pairs with targetExternalId
 * @param targetExternalId upstream id, resolved against {@code watch_target}
 * @param slotKey          identifies the bookable unit within the target — a date,
 *                         a session id, whatever the source uses; opaque here
 * @param status           {@code OPEN} or {@code CLOSED}
 * @param windowStart      start of the bookable window, when the source has one
 * @param windowEnd        end of the bookable window
 * @param quantity         units available, when the source reports it
 * @param meta             opaque per-source extras, stored but never interpreted
 * @param polledAt         when the poller observed this, not when we received it
 */
public record AvailabilityEvent(
        String source,
        String targetExternalId,
        String slotKey,
        String status,
        Instant windowStart,
        Instant windowEnd,
        Integer quantity,
        Map<String, Object> meta,
        Instant polledAt) {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    public AvailabilityEvent {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    /** True when this observation reports the slot as bookable. */
    public boolean isOpen() {
        return STATUS_OPEN.equalsIgnoreCase(status);
    }

    /**
     * True when the event carries the minimum needed to be actionable. An event
     * missing any of these cannot be resolved to a target or a slot, so the
     * matcher drops it rather than guessing.
     */
    public boolean isUsable() {
        return notBlank(source) && notBlank(targetExternalId) && notBlank(slotKey)
                && (isOpen() || STATUS_CLOSED.equalsIgnoreCase(status));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
