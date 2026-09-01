package io.kelta.worker.service.mailbox.inbound;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Turns raw RFC 5322 bytes into a {@link NormalizedInboundMail}.
 *
 * <p>Uses {@code jakarta.mail} — already on the classpath through
 * {@code spring-boot-starter-mail}, which brings the full Angus implementation rather than just
 * the API, so no new dependency is needed to parse.
 *
 * <p>The walking rules below are written down deliberately. Every one of them is a place inbound
 * parsers get it wrong, and the failure mode is always silent: a message that looks fine but has
 * the wrong body, a missing attachment, or mojibake where an accented name should be.
 *
 * @since 1.0.0
 */
@Component
public class MimeParser {

    private static final Logger log = LoggerFactory.getLogger(MimeParser.class);

    /** Headers worth keeping. The full set is large, mostly Received: hops, and mostly noise. */
    private static final List<String> KEPT_HEADERS = List.of(
            "Message-ID", "In-Reply-To", "References", "Date", "Subject", "From", "To", "Cc",
            "Reply-To", "Return-Path", "Auto-Submitted", "Precedence", "List-Id",
            "List-Unsubscribe", "List-Help", "X-Auto-Response-Suppress", "Content-Type",
            "Authentication-Results", "Received-SPF", "X-Original-To", "Delivered-To");

    private static final List<String> LIST_HEADERS = List.of("List-Id", "List-Unsubscribe", "List-Help");

    /** Bodies beyond this are truncated; the raw MIME in object storage stays authoritative. */
    private static final int MAX_BODY_CHARS = 256 * 1024;

    public static class MailParseException extends RuntimeException {
        public MailParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public MailParseException(String message) {
            super(message);
        }
    }

    /**
     * @param raw      the complete message as received
     * @param verdicts authentication results from the receiving MTA, which cannot be recovered
     *                 from the bytes and must be supplied by the adapter
     * @param limits   per-mailbox caps; a message exceeding them is rejected rather than truncated
     */
    public NormalizedInboundMail parse(byte[] raw, NormalizedInboundMail.Verdicts verdicts, Limits limits) {
        if (raw == null || raw.length == 0) {
            throw new MailParseException("Empty message");
        }
        if (raw.length > limits.maxMessageBytes()) {
            throw new MailParseException(
                    "Message is " + raw.length + " bytes, over the mailbox limit of " + limits.maxMessageBytes());
        }
        try {
            Session session = Session.getInstance(new Properties());
            MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));

            BodyParts parts = new BodyParts();
            walk(msg, parts, limits, 0);

            String returnPath = header(msg, "Return-Path");
            String autoSubmitted = header(msg, "Auto-Submitted");
            String precedence = header(msg, "Precedence");
            String contentType = safeContentType(msg);

            return new NormalizedInboundMail(
                    header(msg, "Message-ID"),
                    header(msg, "In-Reply-To"),
                    header(msg, "References"),
                    firstAddress(msg.getFrom()),
                    firstPersonal(msg.getFrom()),
                    addressList(msg.getRecipients(Message.RecipientType.TO)),
                    addressList(msg.getRecipients(Message.RecipientType.CC)),
                    firstAddress(msg.getReplyTo()),
                    decode(msg.getSubject()),
                    truncate(parts.text()),
                    truncate(parts.html()),
                    keptHeaders(msg),
                    verdicts,
                    autoSubmitted,
                    precedence,
                    isBulk(msg, precedence),
                    isBounce(returnPath, contentType),
                    parts.attachments(),
                    msg.getSentDate() == null ? null : msg.getSentDate().toInstant(),
                    raw.length);

        } catch (MailParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MailParseException("Could not parse message: " + e.getMessage(), e);
        }
    }

    /** Per-mailbox ingest caps. */
    public record Limits(long maxMessageBytes, int maxAttachments, long maxAttachmentBytes) {
        public static Limits defaults() {
            return new Limits(26_214_400L, 20, 26_214_400L);
        }
    }

    // ------------------------------------------------------------------ Walking

    private static final class BodyParts {
        private final List<String> textCandidates = new ArrayList<>();
        private final List<String> htmlCandidates = new ArrayList<>();
        private final List<NormalizedInboundMail.ParsedAttachment> attachments = new ArrayList<>();

        /**
         * RFC 2046 orders {@code multipart/alternative} least-faithful first, so the LAST
         * matching part is the one the sender considered best. Taking the first is the classic
         * bug: it yields the plain-text fallback of a rich message, or an empty stub.
         */
        String text() {
            return textCandidates.isEmpty() ? null : textCandidates.getLast();
        }

        String html() {
            return htmlCandidates.isEmpty() ? null : htmlCandidates.getLast();
        }

        List<NormalizedInboundMail.ParsedAttachment> attachments() {
            return attachments;
        }
    }

    private void walk(Part part, BodyParts out, Limits limits, int depth) throws Exception {
        // Bounded because a hostile message can nest multiparts arbitrarily deep, and each level
        // costs a frame. This is a parser DoS guard, not a correctness rule.
        if (depth > 20) {
            log.warn("Stopping MIME walk at depth {} — message is nested implausibly deep", depth);
            return;
        }

        // Attachment-ness is decided FIRST, and by disposition or filename rather than by type.
        // A text/plain part with a filename is an attached .txt, not the body; deciding by MIME
        // type would silently swallow it into the message text.
        if (isAttachment(part)) {
            collectAttachment(part, out, limits);
            return;
        }

        Object content;
        try {
            content = part.getContent();
        } catch (Exception e) {
            // An unknown charset or a broken part must not lose the whole message.
            log.debug("Unreadable MIME part, skipping: {}", e.getMessage());
            return;
        }

        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                walk(multipart.getBodyPart(i), out, limits, depth + 1);
            }
            return;
        }

        // A forwarded message is stored whole as an attachment and NOT descended into. Recursing
        // would let the forwarded mail's body replace the actual message body — so a customer
        // forwarding a receipt would appear to have written the receipt.
        if (part.isMimeType("message/rfc822")) {
            collectAttachment(part, out, limits);
            return;
        }

        if (content instanceof String body) {
            if (part.isMimeType("text/html")) {
                out.htmlCandidates.add(body);
            } else if (part.isMimeType("text/*")) {
                out.textCandidates.add(body);
            }
            return;
        }

        if (content instanceof InputStream in) {
            // Anything non-text with no disposition still has to go somewhere; dropping it would
            // lose an attachment whose sender simply omitted Content-Disposition.
            collectAttachment(part, out, limits, readAll(in));
        }
    }

    private boolean isAttachment(Part part) throws Exception {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        String filename = part.getFileName();
        // An inline part with a filename and no cid: reference is still an attachment.
        return filename != null && !filename.isBlank() && !part.isMimeType("multipart/*");
    }

    private void collectAttachment(Part part, BodyParts out, Limits limits) throws Exception {
        try (InputStream in = part.getInputStream()) {
            collectAttachment(part, out, limits, readAll(in));
        }
    }

    private void collectAttachment(Part part, BodyParts out, Limits limits, byte[] bytes) throws Exception {
        if (out.attachments.size() >= limits.maxAttachments()) {
            log.warn("Dropping attachment beyond the per-message limit of {}", limits.maxAttachments());
            return;
        }
        if (bytes.length > limits.maxAttachmentBytes()) {
            log.warn("Dropping attachment of {} bytes, over the limit of {}",
                    bytes.length, limits.maxAttachmentBytes());
            return;
        }
        String contentId = normaliseContentId(header(part, "Content-ID"));
        out.attachments.add(new NormalizedInboundMail.ParsedAttachment(
                sanitizeFilename(decode(part.getFileName())),
                baseContentType(safeContentType(part)),
                bytes,
                contentId,
                contentId != null || Part.INLINE.equalsIgnoreCase(part.getDisposition())));
    }

    // ------------------------------------------------------------------ Header helpers

    /**
     * Strips anything that could make a filename lie about itself.
     *
     * <p>Path separators, because a filename is never a path. Control characters and bidi
     * overrides, because {@code invoice‮gnp.exe} renders to a human eye as
     * {@code invoice png.exe} — the extension the user thinks they are opening is not the one
     * that will execute.
     */
    static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        String cleaned = filename
                .replaceAll("[\\\\/]", "_")
                .replaceAll("[\\p{Cntrl}]", "")
                // U+202A-U+202E and U+2066-U+2069 reorder rendered text.
                .replaceAll("[‪-‮⁦-⁩​-‏⁠﻿]", "");
        cleaned = cleaned.strip();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "attachment";
        }
        return cleaned.length() > 400 ? cleaned.substring(0, 400) : cleaned;
    }

    private static String normaliseContentId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (v.startsWith("<") && v.endsWith(">")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isBlank() ? null : v;
    }

    private static String baseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0 ? contentType.substring(0, semi) : contentType).trim();
        return base.isEmpty() ? "application/octet-stream" : base.toLowerCase(Locale.ROOT);
    }

    private static String safeContentType(Part part) {
        try {
            return part.getContentType();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> keptHeaders(MimeMessage msg) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : KEPT_HEADERS) {
            String value = header(msg, name);
            if (value != null) {
                out.put(name, value);
            }
        }
        return out;
    }

    private boolean isBulk(MimeMessage msg, String precedence) {
        if (precedence != null) {
            String p = precedence.toLowerCase(Locale.ROOT);
            if (p.contains("bulk") || p.contains("junk") || p.contains("list")) {
                return true;
            }
        }
        return LIST_HEADERS.stream().anyMatch(h -> header(msg, h) != null);
    }

    /**
     * A bounce is either a report-type message or one from the null envelope sender.
     *
     * <p>{@code Return-Path: <>} is how a mail system says "do not reply to this" — replying to
     * a null sender is backscatter, and it is how a domain gets itself blocklisted.
     */
    private boolean isBounce(String returnPath, String contentType) {
        boolean nullSender = returnPath != null && returnPath.replace(" ", "").equals("<>");
        boolean report = contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains("multipart/report");
        return nullSender || report;
    }

    private static String header(Part part, String name) {
        try {
            String[] values = part.getHeader(name);
            if (values == null || values.length == 0) {
                return null;
            }
            String v = values[0];
            return v == null || v.isBlank() ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** RFC 2047 decoding, so an encoded-word subject is not shown as {@code =?utf-8?B?...?=}. */
    private static String decode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        if (addresses[0] instanceof InternetAddress ia && ia.getAddress() != null) {
            return ia.getAddress().toLowerCase(Locale.ROOT);
        }
        return addresses[0].toString().toLowerCase(Locale.ROOT);
    }

    private static String firstPersonal(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        if (addresses[0] instanceof InternetAddress ia) {
            return decode(ia.getPersonal());
        }
        return null;
    }

    private static String addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Address a : addresses) {
            out.add(a instanceof InternetAddress ia && ia.getAddress() != null
                    ? ia.getAddress() : a.toString());
        }
        return String.join(", ", out);
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= MAX_BODY_CHARS
                ? body
                : body.substring(0, MAX_BODY_CHARS) + "\n\n[truncated — see the stored original]";
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    /** Falls back to a text rendering when a sender supplied HTML only. */
    public static String textFallback(String bodyText, String sanitizedHtml) {
        if (bodyText != null && !bodyText.isBlank()) {
            return bodyText;
        }
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return null;
        }
        return org.jsoup.Jsoup.parse(sanitizedHtml).text();
    }

    /**
     * Removes characters that are invisible or that reorder rendered text.
     *
     * <p>Applied to the text a human reads and, later, to anything shown to a model. Zero-width
     * and bidi characters are how instructions get hidden inside a message that looks innocuous.
     */
    public static String normaliseVisibleText(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replaceAll("[​-‏⁠﻿]", "")
                .replaceAll("[‪-‮⁦-⁩]", "")
                .replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
