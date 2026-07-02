package io.kelta.worker.service.campaign;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {@code ${field}} merge placeholders against a variables map.
 *
 * <p>Mirrors the substitution grammar used by
 * {@code io.kelta.worker.service.email.DefaultEmailService} so campaign bodies and
 * transactional templates behave identically. Unknown placeholders are left intact
 * ({@code ${missing}}) so gaps are obvious during QA rather than silently blanked.
 */
public final class MergeFieldRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+)\\}");

    private MergeFieldRenderer() {}

    /**
     * Substitutes {@code ${key}} placeholders in {@code template} with values from {@code vars}.
     *
     * @param template the raw template (may be null → returns empty string)
     * @param vars     substitution variables (may be null → treated as empty)
     * @return the rendered string
     */
    public static String render(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, Object> safe = vars == null ? Map.of() : vars;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = safe.get(key);
            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
