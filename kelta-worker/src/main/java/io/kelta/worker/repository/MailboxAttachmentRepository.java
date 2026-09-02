package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code mailbox_attachment}.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxAttachmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailboxAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param contentType         our verdict from {@code AttachmentContentType.sniff}
     * @param declaredContentType the sender's MIME header, retained but never trusted
     */
    public String insert(String tenantId, String messageId, String mailboxId,
                         String filename, String contentType, String declaredContentType,
                         long sizeBytes, String contentId, boolean inline, String storageKey,
                         String checksumSha256, String scanStatus) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO mailbox_attachment
                    (id, tenant_id, message_id, mailbox_id, filename, content_type,
                     declared_content_type, size_bytes, content_id, inline, storage_key,
                     checksum_sha256, scan_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """, id, tenantId, messageId, mailboxId, filename, contentType,
                declaredContentType, sizeBytes, contentId, inline, storageKey,
                checksumSha256, scanStatus);
        return id;
    }

    /** One attachment, scoped to its message so a caller cannot fetch by id across threads. */
    public Optional<Map<String, Object>> findForDownload(String tenantId, String messageId,
                                                         String attachmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, message_id, mailbox_id, filename, content_type, size_bytes,
                       storage_key, scan_status
                  FROM mailbox_attachment
                 WHERE tenant_id = ? AND message_id = ? AND id = ?
                """, tenantId, messageId, attachmentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<Map<String, Object>> listForMessage(String tenantId, String messageId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM mailbox_attachment
                 WHERE tenant_id = ? AND message_id = ?
                 ORDER BY inline DESC, filename
                """, tenantId, messageId);
    }
}
