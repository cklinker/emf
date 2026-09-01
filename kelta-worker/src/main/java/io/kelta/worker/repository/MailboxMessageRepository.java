package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code mailbox_message}.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailboxMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds the thread a {@code Message-ID} belongs to, scoped to one mailbox.
     *
     * <p>Used to resolve {@code In-Reply-To} and each id in {@code References}. Scoping to the
     * mailbox matters: a {@code Message-ID} is only unique by convention, and a sender can put
     * any value there, so an unscoped lookup would let someone join a thread in a mailbox they
     * never wrote to by guessing or replaying an id.
     */
    public Optional<String> findThreadIdByMessageId(String tenantId, String mailboxId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }
        List<String> ids = jdbcTemplate.queryForList("""
                SELECT thread_id FROM mailbox_message
                 WHERE tenant_id = ? AND mailbox_id = ? AND message_id = ?
                 ORDER BY received_at DESC
                 LIMIT 1
                """, String.class, tenantId, mailboxId, messageId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    /** The parameters of one stored inbound message. */
    public record InboundInsert(
            String tenantId, String threadId, String mailboxId,
            String messageId, String inReplyTo, String references,
            String fromAddress, String fromName, String toAddresses, String ccAddresses,
            String replyToAddress, String subject,
            String bodyText, String bodyHtml, String bodyHtmlSanitized, String snippet,
            String headersJson,
            String spf, String dkim, String dmarc, String dmarcPolicy, String spam, String virus,
            String autoSubmitted, String precedence, boolean bulk, boolean bounce,
            String rawStorageKey, Long rawSizeBytes, Instant sentAt) {
    }

    public String insertInbound(InboundInsert in) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO mailbox_message
                    (id, tenant_id, thread_id, mailbox_id, direction, kind,
                     message_id, in_reply_to, references_header,
                     from_address, from_name, to_addresses, cc_addresses, reply_to_address, subject,
                     body_text, body_html, body_html_sanitized, snippet, headers,
                     spf_result, dkim_result, dmarc_result, dmarc_policy, spam_verdict, virus_verdict,
                     auto_submitted, precedence, is_bulk, is_bounce,
                     raw_storage_key, raw_size_bytes, sent_at, received_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'INBOUND', 'EMAIL',
                        ?, ?, ?,
                        ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?::jsonb,
                        ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?, ?, NOW(), NOW(), NOW())
                """,
                id, in.tenantId(), in.threadId(), in.mailboxId(),
                in.messageId(), in.inReplyTo(), in.references(),
                in.fromAddress(), in.fromName(), in.toAddresses(), in.ccAddresses(),
                in.replyToAddress(), in.subject(),
                in.bodyText(), in.bodyHtml(), in.bodyHtmlSanitized(), in.snippet(), in.headersJson(),
                in.spf(), in.dkim(), in.dmarc(), in.dmarcPolicy(), in.spam(), in.virus(),
                in.autoSubmitted(), in.precedence(), in.bulk(), in.bounce(),
                in.rawStorageKey(), in.rawSizeBytes(),
                in.sentAt() == null ? null : Timestamp.from(in.sentAt()));
        return id;
    }

    /**
     * How many messages this address has sent to this mailbox recently.
     *
     * <p>Feeds the per-sender rate limit. Counted from stored rows rather than a cache so the
     * limit survives a pod restart — a limiter that resets on deploy is not a limiter.
     */
    public int countRecentFromSender(String tenantId, String mailboxId, String fromAddress, int minutes) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM mailbox_message
                 WHERE tenant_id = ? AND mailbox_id = ? AND direction = 'INBOUND'
                   AND lower(from_address) = lower(?)
                   AND received_at >= NOW() - make_interval(mins => ?)
                """, Integer.class, tenantId, mailboxId, fromAddress, minutes);
        return n == null ? 0 : n;
    }
}
