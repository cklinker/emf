package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-channel delivery outcome for an alert.
 *
 * <p>Rows are created PENDING before the send is attempted, so a crash mid-send
 * leaves evidence that a delivery was owed rather than losing it silently. Rows
 * carry no {@code tenant_id}: they are reachable only through their alert, which
 * is tenant-scoped and RLS'd, and the FK cascade ties their lifetimes together —
 * a denormalized copy would be a second source of truth that could drift.
 */
@Repository
public class AlertDeliveryRepository {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    private final JdbcTemplate jdbc;

    public AlertDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Records an owed delivery per channel, returning the new row ids in order. */
    public List<String> createPending(String alertId, List<String> channels) {
        return channels.stream().map(channel -> {
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                            INSERT INTO alert_delivery (id, alert_id, channel, status, created_at)
                            VALUES (?, ?, ?, 'PENDING', NOW())
                            """,
                    id, alertId, channel);
            return id;
        }).toList();
    }

    /** Marks a delivery sent. */
    public void markSent(String deliveryId, Instant sentAt) {
        jdbc.update("UPDATE alert_delivery SET status = 'SENT', sent_at = ? WHERE id = ?",
                java.sql.Timestamp.from(sentAt == null ? Instant.now() : sentAt), deliveryId);
    }

    /**
     * Marks a delivery failed. The error is truncated defensively — a provider
     * stack trace should not be able to bloat the row.
     */
    public void markFailed(String deliveryId, String error) {
        String trimmed = error == null ? null
                : error.length() > 2000 ? error.substring(0, 2000) : error;
        jdbc.update("UPDATE alert_delivery SET status = 'FAILED', error = ? WHERE id = ?",
                trimmed, deliveryId);
    }

    /** Delivery rows for an alert (diagnostics + the slice-5 alert history view). */
    public int countByStatus(String alertId, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_delivery WHERE alert_id = ? AND status = ?",
                Integer.class, alertId, status);
        return count == null ? 0 : count;
    }
}
