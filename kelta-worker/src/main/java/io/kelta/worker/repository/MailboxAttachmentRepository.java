package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
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

    public String insert(String tenantId, String messageId, String mailboxId,
                         String filename, String contentType, long sizeBytes,
                         String contentId, boolean inline, String storageKey,
                         String checksumSha256, String scanStatus) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO mailbox_attachment
                    (id, tenant_id, message_id, mailbox_id, filename, content_type, size_bytes,
                     content_id, inline, storage_key, checksum_sha256, scan_status,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """, id, tenantId, messageId, mailboxId, filename, contentType, sizeBytes,
                contentId, inline, storageKey, checksumSha256, scanStatus);
        return id;
    }

    public List<Map<String, Object>> listForMessage(String tenantId, String messageId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM mailbox_attachment
                 WHERE tenant_id = ? AND message_id = ?
                 ORDER BY inline DESC, filename
                """, tenantId, messageId);
    }
}
