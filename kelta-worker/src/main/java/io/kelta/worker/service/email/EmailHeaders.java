package io.kelta.worker.service.email;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 5322 headers applied to an outbound message beyond To/From/Subject.
 *
 * <p>Exists because a reply is not just another email: it has to land in the right
 * conversation in the recipient's client ({@code In-Reply-To} / {@code References}),
 * it has to route responses back to the thread that produced it ({@code Reply-To}),
 * and — when it was generated rather than typed — it has to announce itself as
 * automated so the far side's autoresponder does not answer it. Two auto-repliers
 * without {@code Auto-Submitted} is a mail loop, and mail loops get sending domains
 * blocked.
 *
 * <p><b>Values here are frequently attacker-derived.</b> {@code inReplyTo} and
 * {@code references} on a reply are copied from the inbound message's own headers,
 * which any sender controls. Every value is therefore validated on construction —
 * see {@link #sanitize}. A CR or LF smuggled into a header value is header injection:
 * it terminates the header and lets the sender append arbitrary headers, or a body,
 * to mail we send under our own authenticated domain.
 *
 * @param replyTo         address responses should go to (a VERP thread token for mailbox replies)
 * @param inReplyTo       the {@code Message-ID} being replied to, angle brackets included
 * @param references      space-separated {@code Message-ID} chain of the conversation
 * @param autoSubmitted   RFC 3834; {@code auto-replied} on anything a human did not send
 * @param precedence      legacy bulk-mail hint, e.g. {@code bulk}
 * @param listUnsubscribe RFC 8058 unsubscribe target
 * @param extra           any further headers, applied verbatim after validation
 */
public record EmailHeaders(
        String replyTo,
        String inReplyTo,
        String references,
        String autoSubmitted,
        String precedence,
        String listUnsubscribe,
        Map<String, String> extra
) {

    /** Longest header value we will emit. Real Message-ID chains stay far below this. */
    private static final int MAX_VALUE_LENGTH = 4000;

    public EmailHeaders {
        replyTo = sanitize("Reply-To", replyTo);
        inReplyTo = sanitize("In-Reply-To", inReplyTo);
        references = sanitize("References", references);
        autoSubmitted = sanitize("Auto-Submitted", autoSubmitted);
        precedence = sanitize("Precedence", precedence);
        listUnsubscribe = sanitize("List-Unsubscribe", listUnsubscribe);

        if (extra == null || extra.isEmpty()) {
            extra = Map.of();
        } else {
            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : extra.entrySet()) {
                String name = e.getKey();
                if (name == null || name.isBlank()) {
                    continue;
                }
                // A header NAME may only be printable US-ASCII excluding colon (RFC 5322 3.6.8).
                // Anything else is a caller bug or an injection attempt; drop the pair rather
                // than emitting a header we cannot reason about.
                if (!name.chars().allMatch(c -> c > 32 && c < 127 && c != ':')) {
                    throw new IllegalArgumentException("Illegal header name: " + name);
                }
                String value = sanitize(name, e.getValue());
                if (value != null) {
                    copy.put(name, value);
                }
            }
            extra = Map.copyOf(copy);
        }
    }

    /** No extra headers — the value every existing (non-reply) send path uses. */
    public static EmailHeaders none() {
        return new EmailHeaders(null, null, null, null, null, null, Map.of());
    }

    public boolean isEmpty() {
        return replyTo == null && inReplyTo == null && references == null
                && autoSubmitted == null && precedence == null
                && listUnsubscribe == null && extra.isEmpty();
    }

    /**
     * Rejects header injection and normalises blank to null.
     *
     * <p>Throws rather than silently stripping: a value containing CR/LF did not get
     * there by accident, and quietly sending a truncated version of an attacker's
     * header hides the attempt. The caller (the mailbox reply path) catches this and
     * drops the offending header, recording why.
     */
    private static String sanitize(String name, String value) {
        if (value == null) {
            return null;
        }
        // Validate BEFORE trimming. String.trim() removes every character <= U+0020,
        // which includes CR, LF and NUL — so trimming first would silently accept the
        // most common injection shape of all, a value with a trailing CRLF, and this
        // method would quietly strip where it claims to reject.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') {
                throw new IllegalArgumentException(
                        "Header " + name + " contains an illegal control character (CR/LF/NUL)");
            }
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "Header " + name + " exceeds " + MAX_VALUE_LENGTH + " characters");
        }
        return trimmed;
    }
}
