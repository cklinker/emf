package io.kelta.auth.controller;

/**
 * Makes caller-supplied values safe to interpolate into audit lines.
 *
 * <p>The public portal endpoints log the submitted email as {@code actor=} before
 * anything has validated it — that is the point, an unrecognised address still
 * has to be auditable. But the audit stream is line-oriented, so an address
 * containing a newline lets the caller append a second, entirely fabricated
 * record: the log becomes attacker-writable exactly where it is relied on to
 * establish what happened.
 */
final class AuditText {

    /** Longest value worth keeping; anything beyond this is padding, not identity. */
    private static final int MAX_LENGTH = 256;

    private AuditText() {
    }

    /**
     * Returns {@code value} with line breaks and control characters replaced, and
     * bounded in length. Null becomes the literal {@code "unknown"} so a field is
     * never silently absent from a record.
     */
    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            // Space is kept; everything below it (CR, LF, TAB, NUL…) and DEL are
            // the characters that can forge or truncate a record.
            out.append(c < ' ' || c == 127 ? '_' : c);
        }
        return out.toString();
    }
}
