package io.kelta.worker.service.mailbox.inbound;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One inbound message, in the single shape the ingest pipeline understands.
 *
 * <p>This record is the seam that makes the feature provider-agnostic. It is produced either by
 * parsing raw MIME (SES, CloudMailin raw) or by an adapter mapping a provider's already-parsed
 * JSON (Postmark, Mailgun). {@code MailboxIngestService} sees only this, and knows nothing about
 * where mail came from — so adding a provider is one adapter class, not a change to the pipeline.
 *
 * @param messageId       RFC 5322 {@code Message-ID}, angle brackets included
 * @param inReplyTo       {@code In-Reply-To}, if the sender's client set one
 * @param references      raw {@code References} header, space-separated ids
 * @param fromAddress     the parsed {@code From} address, lowercased
 * @param fromName        the {@code From} display name — attacker-controlled, never shown alone
 * @param toAddresses     every {@code To} recipient, comma-joined, for display only
 * @param ccAddresses     every {@code Cc} recipient, comma-joined, for display only
 * @param replyToAddress  {@code Reply-To}, if present
 * @param subject         decoded subject
 * @param bodyText        the plain-text body, or a text rendering of the HTML if none was sent
 * @param bodyHtml        the HTML body exactly as received — evidence, never rendered as-is
 * @param headers         a selected subset of headers, never the full set
 * @param verdicts        the receiving MTA's authentication results
 * @param autoSubmitted   RFC 3834 {@code Auto-Submitted}, the primary loop signal
 * @param precedence      legacy {@code Precedence} header
 * @param bulk            true when list headers or a bulk precedence were present
 * @param bounce          true when this is a DSN/MDN or arrived from a null envelope sender
 * @param attachments     parts that are attachments rather than body
 * @param sentAt          the {@code Date} header, or null when absent or unparseable
 * @param rawSizeBytes    size of the raw MIME
 */
public record NormalizedInboundMail(
        String messageId,
        String inReplyTo,
        String references,
        String fromAddress,
        String fromName,
        String toAddresses,
        String ccAddresses,
        String replyToAddress,
        String subject,
        String bodyText,
        String bodyHtml,
        Map<String, String> headers,
        Verdicts verdicts,
        String autoSubmitted,
        String precedence,
        boolean bulk,
        boolean bounce,
        List<ParsedAttachment> attachments,
        Instant sentAt,
        long rawSizeBytes
) {

    public NormalizedInboundMail {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        verdicts = verdicts == null ? Verdicts.unknown() : verdicts;
    }

    /**
     * What the receiving mail system concluded about the sender.
     *
     * <p>Computed by the MTA that actually accepted the connection and never recomputed
     * downstream: SPF is a property of the connecting IP, which is gone by the time we see
     * the message, so anything we derived later would be a guess wearing a verdict's clothes.
     *
     * <p>All fields are nullable — a provider that reports nothing yields
     * {@link #unknown()}, and the pipeline must treat that as unverified rather than as pass.
     */
    public record Verdicts(String spf, String dkim, String dmarc, String dmarcPolicy,
                           String spam, String virus) {

        private static final Verdicts UNKNOWN = new Verdicts(null, null, null, null, null, null);

        public static Verdicts unknown() {
            return UNKNOWN;
        }

        public boolean dmarcPassed() {
            return "PASS".equalsIgnoreCase(dmarc);
        }

        public boolean virusFailed() {
            return "FAIL".equalsIgnoreCase(virus);
        }

        public boolean spamFailed() {
            return "FAIL".equalsIgnoreCase(spam);
        }
    }

    /**
     * One attachment, held in memory.
     *
     * <p>Bounded by the mailbox's configured caps before it reaches here — the parser refuses
     * oversized messages rather than materialising them.
     *
     * @param inline true for a part referenced by {@code cid:} from the HTML body
     */
    public record ParsedAttachment(String filename, String contentType, byte[] content,
                                   String contentId, boolean inline) {

        public long size() {
            return content == null ? 0 : content.length;
        }
    }
}
