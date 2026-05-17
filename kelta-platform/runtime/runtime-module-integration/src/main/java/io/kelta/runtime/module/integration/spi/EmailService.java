package io.kelta.runtime.module.integration.spi;

import java.util.Map;
import java.util.Optional;

/**
 * SPI interface for email operations used by the EmailAlertActionHandler and
 * platform lifecycle code (user invite, password reset, MFA notifications, etc.).
 *
 * <p>Implementations provide template lookup, variable substitution, and email
 * queuing. The host application provides the real implementation backed by a
 * database and mail sender. A no-op logging implementation is provided for testing.
 *
 * @since 1.0.0
 */
public interface EmailService {

    /**
     * Retrieves an email template by ID.
     *
     * @param templateId the template ID
     * @return the template, or empty if not found
     */
    Optional<EmailTemplate> getTemplate(String templateId);

    /**
     * Queues an email for delivery.
     *
     * @param tenantId the tenant ID
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML)
     * @param source the source of the email (e.g., "WORKFLOW")
     * @param sourceId the source ID (e.g., workflow rule ID)
     * @return the generated email log ID
     */
    String queueEmail(String tenantId, String to, String subject, String body,
                      String source, String sourceId);

    /**
     * Looks up a template by stable {@code templateKey} (tenant override or
     * platform default), substitutes {@code ${var}} placeholders from {@code vars},
     * and queues the result.
     *
     * @param tenantId    owning tenant
     * @param to          recipient email address
     * @param templateKey stable key (e.g. "user.invite")
     * @param vars        variables for {@code ${name}} substitution in subject + body
     * @param source      source tag for {@code email_log} (e.g. "USER_INVITE")
     * @param sourceId    optional source row id (e.g. user id)
     * @return the generated email log id; empty if the template is missing
     */
    Optional<String> sendByKey(String tenantId, String to, String templateKey,
                               Map<String, Object> vars, String source, String sourceId);

    /**
     * Email template data.
     *
     * @param subject the template subject
     * @param bodyHtml the template HTML body
     */
    record EmailTemplate(String subject, String bodyHtml) {}
}
