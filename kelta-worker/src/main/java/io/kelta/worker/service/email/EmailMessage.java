package io.kelta.worker.service.email;

import java.util.List;

/**
 * Immutable representation of an email to be sent.
 *
 * <p>Does not include SMTP configuration or sender details — those are resolved
 * from platform defaults or per-tenant overrides at delivery time.
 *
 * @param to          recipient email address
 * @param subject     email subject line
 * @param bodyHtml    HTML body content
 * @param bodyText    optional plain-text fallback (may be null)
 * @param attachments files attached to the message (never null; empty for plain emails)
 * @param headers     RFC 5322 headers beyond To/From/Subject (never null; see {@link EmailHeaders})
 */
public record EmailMessage(
        String to,
        String subject,
        String bodyHtml,
        String bodyText,
        List<EmailAttachment> attachments,
        EmailHeaders headers
) {

    public EmailMessage {
        attachments = attachments != null ? List.copyOf(attachments) : List.of();
        headers = headers != null ? headers : EmailHeaders.none();
    }

    /**
     * Five-arg form kept explicitly so every pre-existing caller compiles unchanged
     * after {@code headers} was added as a sixth component. Do not delete.
     */
    public EmailMessage(String to, String subject, String bodyHtml, String bodyText,
                        List<EmailAttachment> attachments) {
        this(to, subject, bodyHtml, bodyText, attachments, EmailHeaders.none());
    }

    public EmailMessage(String to, String subject, String bodyHtml, String bodyText) {
        this(to, subject, bodyHtml, bodyText, List.of(), EmailHeaders.none());
    }
}
