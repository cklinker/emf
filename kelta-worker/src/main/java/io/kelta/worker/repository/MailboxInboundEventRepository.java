package io.kelta.worker.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * The inbound idempotency ledger.
 *
 * <p>Not optional. SNS retries on any non-2xx <i>and</i> can deliver at-least-once even when the
 * endpoint returned 200, so without a claim table one customer email becomes several threads,
 * several notifications and — once auto-reply exists — several replies.
 *
 * <p>The claim is the {@code INSERT}: whoever wins the unique index owns the message, and every
 * other delivery of it gets zero rows back and stops. No locking, no read-then-write race.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxInboundEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailboxInboundEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Attempts to claim a delivery.
     *
     * <p>Claimed <b>before</b> parsing, deliberately. A message that crashes the parser must not
     * be retried by SNS forever; recording it first means the failure is durable and the retry
     * stops, at the cost that a crash between claim and parse loses that one message. The raw
     * MIME is persisted to object storage before parsing so such a message can be re-driven from
     * the ledger rather than lost outright.
     *
     * @return the new event id, or empty when this delivery has already been seen
     */
    public java.util.Optional<String> claim(String tenantId, String mailboxId, String provider,
                                            String providerEventId, String payloadDigest) {
        String id = UUID.randomUUID().toString();
        try {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO mailbox_inbound_event
                        (id, tenant_id, mailbox_id, provider, provider_event_id, payload_digest,
                         status, received_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', NOW(), NOW(), NOW())
                    ON CONFLICT DO NOTHING
                    """, id, tenantId, mailboxId, provider, providerEventId, payloadDigest);
            return inserted == 1 ? java.util.Optional.of(id) : java.util.Optional.empty();
        } catch (DuplicateKeyException e) {
            // ON CONFLICT DO NOTHING covers the declared unique indexes; this catches anything
            // else that races and is treated the same way — someone else owns this delivery.
            return java.util.Optional.empty();
        }
    }

    public void markRouted(String id, String tenantId, String messageId, String rawStorageKey) {
        jdbcTemplate.update("""
                UPDATE mailbox_inbound_event
                   SET status = 'ROUTED', message_id = ?, raw_storage_key = ?,
                       processed_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, messageId, rawStorageKey, id, tenantId);
    }

    public void markRawStored(String id, String tenantId, String rawStorageKey) {
        jdbcTemplate.update("""
                UPDATE mailbox_inbound_event
                   SET raw_storage_key = ?, updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, rawStorageKey, id, tenantId);
    }

    /**
     * Records a terminal outcome that produced no message.
     *
     * <p>{@code reason} is truncated rather than allowed to overflow the column: it is derived
     * from parser output and therefore from attacker-supplied bytes.
     */
    public void markRejected(String id, String tenantId, String status, String reason) {
        jdbcTemplate.update("""
                UPDATE mailbox_inbound_event
                   SET status = ?, reject_reason = ?, processed_at = NOW(), updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, status, truncate(reason), id, tenantId);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 200 ? s : s.substring(0, 197) + "...";
    }
}
