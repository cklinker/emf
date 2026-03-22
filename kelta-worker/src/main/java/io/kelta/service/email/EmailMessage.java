package io.kelta.worker.service.email;

/**
 * Immutable representation of an email to be sent.
 *
 * <p>Does not include SMTP configuration or sender details — those are resolved
 * from platform defaults or per-tenant overrides at delivery time.
 *
 * @param to        recipient email address
 * @param subject   email subject line
 * @param bodyHtml  HTML body content
 * @param bodyText  optional plain-text fallback (may be null)
 */
public record EmailMessage(
        String to,
        String subject,
        String bodyHtml,
        String bodyText
) {}
