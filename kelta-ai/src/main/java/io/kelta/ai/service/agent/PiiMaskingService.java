package io.kelta.ai.service.agent;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Redacts common PII patterns from text before it is sent to the LLM. The governed agent runtime
 * applies this to <em>tool results</em> — data the agent pulled from the platform, which may contain
 * other people's personal data — so that platform PII is not exposed to the external model or stored
 * verbatim in the audit trail. User-supplied input is intentionally <em>not</em> masked (it is the
 * caller's own prompt).
 *
 * <p>Pattern-based and conservative: it favours redacting recognisable emails, US SSNs, 16-digit
 * card numbers and NANP phone numbers. It is a safety net, not a guarantee.
 */
@Service
public class PiiMaskingService {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SSN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD = Pattern.compile(
            "\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b");
    private static final Pattern PHONE = Pattern.compile(
            "\\b(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");

    /**
     * Returns {@code text} with recognised PII replaced by {@code [REDACTED_*]} placeholders.
     * Null/blank input is returned unchanged. Order matters: SSN and card numbers are redacted
     * before the phone pattern so they aren't partially swallowed by it.
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
        out = SSN.matcher(out).replaceAll("[REDACTED_SSN]");
        out = CREDIT_CARD.matcher(out).replaceAll("[REDACTED_CC]");
        out = PHONE.matcher(out).replaceAll("[REDACTED_PHONE]");
        return out;
    }
}
