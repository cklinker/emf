package io.kelta.worker.service.mailbox;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Decides what an attachment actually is, and what we are willing to say it is.
 *
 * <p>The MIME part header is a claim made by whoever sent the mail. It costs an attacker nothing to
 * label an HTML document {@code image/png}, and until this class existed that label was stored as
 * {@code mailbox_attachment.content_type} and would have been echoed back on download — while both
 * the migration and the ingest service carried comments asserting the type was "determined by our
 * own sniffing, never taken from the MIME part header". It was not. This is that sniffing.
 *
 * <p>Two separate questions, deliberately kept apart:
 *
 * <ul>
 *   <li>{@link #sniff} — what do the bytes say this is? Recorded as the content type, and used to
 *       decide whether the file is safe to hand a browser.</li>
 *   <li>{@link #serveAs} — what may we put in a {@code Content-Type} response header? Anything a
 *       browser might execute or treat as same-origin markup is served as
 *       {@code application/octet-stream}, whatever the bytes say.</li>
 * </ul>
 *
 * <p>Sniffing is not a security boundary on its own — a file can be valid in two formats at once
 * (a polyglot), and a byte prefix is a heuristic. It narrows what we will claim; the actual
 * protection on the download path is {@code Content-Disposition: attachment} plus
 * {@code X-Content-Type-Options: nosniff}, which hold regardless of what this class returns.
 *
 * @since 1.0.0
 */
public final class AttachmentContentType {

    /** What we fall back to whenever the bytes do not clearly say otherwise. */
    public static final String OCTET_STREAM = "application/octet-stream";

    /**
     * Types a browser may execute, or that carry script, or that can act with the origin's
     * authority if a response is ever rendered rather than downloaded. Never served as themselves.
     *
     * <p>SVG is on this list and belongs there: it is an XML document that can carry
     * {@code <script>} and event handlers, and it is the one image type that is also a document.
     */
    private static final Set<String> ACTIVE_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/xml",
            "application/xml",
            "application/javascript",
            "text/javascript",
            "application/x-javascript",
            "text/vbscript",
            "application/xslt+xml",
            "text/css",
            "application/pdf");

    private AttachmentContentType() {
    }

    /**
     * Identifies content by its leading bytes.
     *
     * <p>Returns {@link #OCTET_STREAM} when nothing matches, which is the safe answer: an unknown
     * file is one we decline to describe, not one we guess about.
     *
     * @param content the attachment bytes; null or empty yields {@link #OCTET_STREAM}
     * @return a lowercase MIME type derived from the content itself
     */
    public static String sniff(byte[] content) {
        if (content == null || content.length == 0) {
            return OCTET_STREAM;
        }

        // Binary signatures first: these are unambiguous and cheap.
        if (starts(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (starts(content, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(content, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (starts(content, 'B', 'M')) {
            return "image/bmp";
        }
        if (starts(content, 0x00, 0x00, 0x01, 0x00)) {
            return "image/x-icon";
        }
        if (starts(content, 'I', 'I', 0x2A, 0x00) || starts(content, 'M', 'M', 0x00, 0x2A)) {
            return "image/tiff";
        }
        if (starts(content, 'R', 'I', 'F', 'F') && matchesAt(content, 8, "WEBP")) {
            return "image/webp";
        }
        if (starts(content, 'R', 'I', 'F', 'F') && matchesAt(content, 8, "WAVE")) {
            return "audio/wav";
        }
        if (starts(content, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (starts(content, 0x1F, 0x8B)) {
            return "application/gzip";
        }
        if (starts(content, 'B', 'Z', 'h')) {
            return "application/x-bzip2";
        }
        if (starts(content, 0xFD, '7', 'z', 'X', 'Z', 0x00)) {
            return "application/x-xz";
        }
        if (starts(content, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C)) {
            return "application/x-7z-compressed";
        }
        if (starts(content, 'R', 'a', 'r', '!', 0x1A, 0x07)) {
            return "application/vnd.rar";
        }
        if (starts(content, 'P', 'K', 0x03, 0x04) || starts(content, 'P', 'K', 0x05, 0x06)) {
            // Every OOXML document and every JAR is a zip. Distinguishing them means reading the
            // central directory, which is not worth it here: the download path treats all of them
            // identically, and the declared type is retained separately for display.
            return "application/zip";
        }
        if (starts(content, 'I', 'D', '3') || starts(content, 0xFF, 0xFB)) {
            return "audio/mpeg";
        }
        if (starts(content, 'O', 'g', 'g', 'S')) {
            return "application/ogg";
        }
        if (matchesAt(content, 4, "ftyp")) {
            return "video/mp4";
        }
        if (starts(content, '{', 0x5C, 'r', 't', 'f')) {
            return "application/rtf";
        }
        if (starts(content, 0xD0, 0xCF, 0x11, 0xE0)) {
            return "application/vnd.ms-office";
        }
        // Executables. Named rather than lumped into octet-stream so an operator reading the row
        // can see what actually arrived.
        if (starts(content, 'M', 'Z')) {
            return "application/vnd.microsoft.portable-executable";
        }
        if (starts(content, 0x7F, 'E', 'L', 'F')) {
            return "application/x-elf";
        }
        if (starts(content, 0xCA, 0xFE, 0xBA, 0xBE)) {
            return "application/java-vm";
        }

        return sniffText(content);
    }

    /**
     * Distinguishes markup from plain text, which is the distinction that matters here.
     *
     * <p>Leading whitespace and a UTF-8 BOM are skipped before matching, because a browser skips
     * them too when deciding whether a document is HTML. Matching only at byte zero would let
     * {@code "\n<html>"} sniff as {@code text/plain} and then render as markup.
     */
    private static String sniffText(byte[] content) {
        int from = 0;
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            from = 3;
        }
        while (from < content.length && Character.isWhitespace(content[from])) {
            from++;
        }

        int window = Math.min(content.length - from, 1024);
        if (window <= 0) {
            return "text/plain";
        }
        String head = new String(content, from, window, StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);

        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<head")
                || head.startsWith("<body") || head.startsWith("<script")) {
            return "text/html";
        }
        if (head.startsWith("<svg")) {
            return "image/svg+xml";
        }
        if (head.startsWith("<?xml")) {
            // An XML declaration says nothing about the vocabulary. An SVG or XHTML root within
            // the first window is what matters, because that is what a browser would render.
            if (head.contains("<svg")) {
                return "image/svg+xml";
            }
            if (head.contains("<html")) {
                return "application/xhtml+xml";
            }
            return "application/xml";
        }
        if (head.startsWith("{") || head.startsWith("[")) {
            return "application/json";
        }
        if (head.startsWith("%!ps")) {
            return "application/postscript";
        }
        return isProbablyText(content) ? "text/plain" : OCTET_STREAM;
    }

    /**
     * True when the bytes look like text rather than an unrecognised binary.
     *
     * <p>A NUL byte is the strongest signal of binary content, and a high proportion of other
     * control characters is the next. Getting this wrong is not dangerous — both answers are
     * downloaded, never rendered — it only changes what the console displays.
     */
    private static boolean isProbablyText(byte[] content) {
        int window = Math.min(content.length, 4096);
        int suspicious = 0;
        for (int i = 0; i < window; i++) {
            int b = content[i] & 0xFF;
            if (b == 0x00) {
                return false;
            }
            boolean printable = b >= 0x20 || b == '\t' || b == '\n' || b == '\r' || b == 0x0C;
            if (!printable) {
                suspicious++;
            }
        }
        return suspicious * 100 <= window * 5;
    }

    /**
     * The type we are willing to put on a response, given what the bytes turned out to be.
     *
     * <p>Anything on {@link #ACTIVE_TYPES} collapses to {@link #OCTET_STREAM}. That includes PDF,
     * which is not markup but which browsers render in a plugin with its own scripting engine and
     * its own history of sandbox escapes — and which nothing here needs rendered inline.
     *
     * @param sniffed the result of {@link #sniff}
     * @return a type safe to send in a {@code Content-Type} header
     */
    public static String serveAs(String sniffed) {
        if (sniffed == null || sniffed.isBlank()) {
            return OCTET_STREAM;
        }
        String normalised = sniffed.trim().toLowerCase(Locale.ROOT);
        return ACTIVE_TYPES.contains(normalised) ? OCTET_STREAM : normalised;
    }

    /** True when the browser would execute, script or same-origin-render this type. */
    public static boolean isActive(String contentType) {
        return contentType != null && ACTIVE_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean starts(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAt(byte[] content, int offset, String ascii) {
        if (content.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if ((content[offset + i] & 0xFF) != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
