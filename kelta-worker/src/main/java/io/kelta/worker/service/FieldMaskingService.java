package io.kelta.worker.service;

import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies a field's data-masking policy to a value. Stateless, pure transform:
 * the decision of <em>whether</em> a user sees masked data is made by Cerbos
 * (the {@code unmask} action on the {@code field} resource); this service only
 * knows <em>how</em> a value masks.
 *
 * <p>The policy lives in the field's {@code fieldTypeConfig} under key
 * {@code "masking"}:
 *
 * <pre>{@code
 * { "masking": { "type": "FULL" | "LAST4" | "EMAIL" | "CUSTOM",
 *                "maskChar": "*",                 // optional, default "*"
 *                "customPattern": "***-**-####" } } // CUSTOM only
 * }</pre>
 *
 * <p>Only string-typed field types are maskable — a masked value is a string,
 * and emitting one into a number/date/boolean attribute would break JSON:API
 * typing and every typed UI control. For non-string sensitive fields, admins
 * use the {@code HIDDEN} visibility instead. {@link #configFor} returns empty
 * for non-maskable types regardless of configuration (defense in depth), and a
 * present-but-unparseable config degrades to {@code FULL} rather than to
 * plaintext (fail-closed).
 */
@Service
public class FieldMaskingService {

    /** String-typed field types eligible for masking. */
    public static final Set<FieldType> MASKABLE_TYPES = EnumSet.of(
            FieldType.STRING, FieldType.TEXT, FieldType.RICH_TEXT,
            FieldType.EMAIL, FieldType.PHONE, FieldType.URL,
            FieldType.ENCRYPTED, FieldType.EXTERNAL_ID);

    /** Config key inside {@code fieldTypeConfig}. */
    public static final String CONFIG_KEY = "masking";

    private static final char DEFAULT_MASK_CHAR = '*';
    private static final int FULL_MASK_LENGTH = 6;
    private static final int LAST4_KEEP = 4;

    public enum MaskType { FULL, LAST4, EMAIL, CUSTOM }

    /**
     * Parsed masking policy for one field.
     *
     * @param type          how the value masks
     * @param maskChar      character used for masked positions
     * @param customPattern CUSTOM only: right-aligned pattern where {@code '#'}
     *                      passes the source character through and any other
     *                      character is emitted literally
     */
    public record MaskingConfig(MaskType type, char maskChar, String customPattern) {}

    /**
     * Returns the masking policy for a field, or empty when the field has no
     * masking config or its type is not maskable (even if config is present).
     */
    public Optional<MaskingConfig> configFor(FieldDefinition field) {
        if (field == null || field.type() == null || !MASKABLE_TYPES.contains(field.type())) {
            return Optional.empty();
        }
        Object raw = field.getConfigValue(CONFIG_KEY);
        if (!(raw instanceof Map<?, ?> cfg) || cfg.isEmpty()) {
            return Optional.empty();
        }

        // Present-but-invalid type falls back to FULL: an admin who configured
        // masking intended redaction; a typo must not silently yield plaintext.
        MaskType type = MaskType.FULL;
        if (cfg.get("type") instanceof String s && !s.isBlank()) {
            try {
                type = MaskType.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // keep FULL
            }
        }

        char maskChar = DEFAULT_MASK_CHAR;
        if (cfg.get("maskChar") instanceof String mc && !mc.isEmpty()) {
            maskChar = mc.charAt(0);
        }

        String pattern = cfg.get("customPattern") instanceof String p && !p.isBlank() ? p : null;
        if (type == MaskType.CUSTOM && pattern == null) {
            type = MaskType.FULL; // CUSTOM without a pattern degrades to FULL
        }
        return Optional.of(new MaskingConfig(type, maskChar, pattern));
    }

    /**
     * Masks a value under the given policy. Null input returns null (null-ness
     * is not treated as sensitive); blank input returns the fixed FULL mask so
     * output never confirms emptiness of a non-null value.
     */
    public String mask(String value, MaskingConfig config) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return fullMask(config.maskChar());
        }
        return switch (config.type()) {
            case FULL -> fullMask(config.maskChar());
            case LAST4 -> maskLast4(value, config.maskChar());
            case EMAIL -> maskEmail(value, config.maskChar());
            case CUSTOM -> maskCustom(value, config.customPattern(), config.maskChar());
        };
    }

    /** Fixed-length mask — never leaks the original value's length. */
    private static String fullMask(char maskChar) {
        return String.valueOf(maskChar).repeat(FULL_MASK_LENGTH);
    }

    /**
     * Keeps the last four alphanumerics, masks every other alphanumeric, and
     * preserves separators: {@code 123-45-6789 → ***-**-6789}. Values with four
     * or fewer alphanumerics mask entirely (keeping them would leak the whole
     * value).
     */
    private static String maskLast4(String value, char maskChar) {
        long alnumCount = value.chars().filter(Character::isLetterOrDigit).count();
        if (alnumCount <= LAST4_KEEP) {
            return fullMask(maskChar);
        }
        StringBuilder out = new StringBuilder(value.length());
        long toMask = alnumCount - LAST4_KEEP;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) && toMask > 0) {
                out.append(maskChar);
                toMask--;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code craig@rzware.com → c***@rzware.com}; no {@code @} → FULL mask. */
    private static String maskEmail(String value, char maskChar) {
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return fullMask(maskChar);
        }
        return value.charAt(0) + String.valueOf(maskChar).repeat(3) + value.substring(at);
    }

    /**
     * Applies the pattern right-aligned: {@code '#'} passes the source character
     * at the same position from the right (or {@code maskChar} when the value is
     * shorter), any other pattern character is emitted literally. Output length
     * always equals the pattern length — no length leak.
     */
    private static String maskCustom(String value, String pattern, char maskChar) {
        StringBuilder out = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char p = pattern.charAt(i);
            if (p == '#') {
                int fromRight = pattern.length() - 1 - i;
                int valueIdx = value.length() - 1 - fromRight;
                out.append(valueIdx >= 0 ? value.charAt(valueIdx) : maskChar);
            } else {
                out.append(p);
            }
        }
        return out.toString();
    }
}
