package io.kelta.worker.service.email;

/**
 * SPI for email delivery providers.
 *
 * <p>The platform ships with {@link SmtpEmailProvider} as the opinionated default
 * (SMTP, RFC 5321). Users can implement this interface to add custom providers
 * (e.g., SendGrid API, Amazon SES API, Mailgun API) and register them as Spring beans.
 *
 * <p>Implementations receive tenant-specific settings that may override platform defaults
 * for SMTP host, credentials, and sender address.
 *
 * @since 1.0.0
 */
public interface EmailProvider {

    /**
     * Sends an email message using the resolved provider configuration.
     *
     * @param message        the email content (recipient, subject, body)
     * @param tenantSettings tenant-specific email settings, or {@code null} to use platform defaults
     * @throws EmailDeliveryException if delivery fails (must not include credentials in message)
     */
    void send(EmailMessage message, TenantEmailSettings tenantSettings) throws EmailDeliveryException;
}
