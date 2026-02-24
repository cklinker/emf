package com.emf.runtime.module.integration.spi;

import java.util.Optional;

/**
 * SPI interface for email operations used by the EmailAlertActionHandler.
 *
 * <p>Implementations provide email template lookup and email queuing functionality.
 * The host application provides the real implementation backed by a database and
 * mail sender. A no-op logging implementation is provided for testing.
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
     * Email template data.
     *
     * @param subject the template subject
     * @param bodyHtml the template HTML body
     */
    record EmailTemplate(String subject, String bodyHtml) {}
}
