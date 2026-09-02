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

    /**
     * Sends, and reports what the provider stamped on the message.
     *
     * <p>Defaulted rather than added to the interface proper so that existing
     * third-party implementations keep compiling: they inherit a correct — if
     * uninformative — implementation that sends and reports nothing.
     *
     * <p>Override this when the provider can report the outbound {@code Message-ID}.
     * Threaded conversations need it: it is the key a recipient's reply carries back
     * in {@code In-Reply-To}, and a provider that cannot report one can only support
     * conversations threaded by other means.
     *
     * @return what the provider knows about the sent message; never {@code null}
     * @throws EmailDeliveryException if delivery fails (must not include credentials in message)
     */
    default SendResult sendAndReport(EmailMessage message, TenantEmailSettings tenantSettings)
            throws EmailDeliveryException {
        send(message, tenantSettings);
        // send() returning normally means the provider accepted it; only the id is unknown.
        return SendResult.sent(null);
    }
}
