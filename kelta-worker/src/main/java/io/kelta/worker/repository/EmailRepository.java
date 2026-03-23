package io.kelta.worker.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for email templates and email log persistence.
 *
 * <p>Uses JdbcTemplate to match the worker's existing data access pattern.
 * Operates against the {@code email_template} and {@code email_log} tables
 * created in V25.
 *
 * @since 1.0.0
 */
@Repository
public class EmailRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EmailRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Email Templates
    // -----------------------------------------------------------------------

    /**
     * Retrieves an email template by tenant and ID.
     */
    public Optional<Map<String, Object>> findTemplateByTenantAndId(String tenantId, String templateId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, subject, body_html, body_text FROM email_template " +
                        "WHERE tenant_id = ? AND id = ? AND is_active = true",
                tenantId, templateId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // -----------------------------------------------------------------------
    // Email Log
    // -----------------------------------------------------------------------

    /**
     * Creates a new email log entry with status QUEUED.
     *
     * @return the generated log ID
     */
    public String createEmailLog(String tenantId, String recipientEmail, String subject,
                                 String source, String sourceId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO email_log (id, tenant_id, recipient_email, subject, status, source, source_id, created_at) " +
                        "VALUES (?, ?, ?, ?, 'QUEUED', ?, ?, ?)",
                id, tenantId, recipientEmail, subject, source, sourceId, Timestamp.from(Instant.now())
        );
        return id;
    }

    /**
     * Updates the email log status to SENDING.
     */
    public void markSending(String logId) {
        jdbcTemplate.update(
                "UPDATE email_log SET status = 'SENDING' WHERE id = ?",
                logId
        );
    }

    /**
     * Updates the email log status to SENT with the SMTP host used.
     */
    public void markSent(String logId, String smtpHost) {
        jdbcTemplate.update(
                "UPDATE email_log SET status = 'SENT', sent_at = ?, smtp_host = ? WHERE id = ?",
                Timestamp.from(Instant.now()), smtpHost, logId
        );
    }

    /**
     * Updates the email log status to FAILED with error details.
     */
    public void markFailed(String logId, String errorMessage, String smtpHost) {
        jdbcTemplate.update(
                "UPDATE email_log SET status = 'FAILED', error_message = ?, smtp_host = ? WHERE id = ?",
                errorMessage, smtpHost, logId
        );
    }

    // -----------------------------------------------------------------------
    // Tenant Settings
    // -----------------------------------------------------------------------

    /**
     * Retrieves the settings JSONB for a tenant.
     *
     * @return the parsed JsonNode, or null if tenant not found
     */
    public JsonNode getTenantSettings(String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT settings FROM tenant WHERE id = ?",
                tenantId
        );
        if (results.isEmpty()) {
            return null;
        }
        Object settings = results.get(0).get("settings");
        if (settings == null) {
            return null;
        }
        try {
            if (settings instanceof String s) {
                return objectMapper.readTree(s);
            }
            return objectMapper.valueToTree(settings);
        } catch (Exception e) {
            return null;
        }
    }
}
