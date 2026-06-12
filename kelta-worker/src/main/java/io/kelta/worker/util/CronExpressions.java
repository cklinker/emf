package io.kelta.worker.util;

import org.springframework.scheduling.support.CronExpression;

/**
 * Helpers for parsing and normalizing cron expressions used by the scheduler.
 *
 * <p>Spring's {@link CronExpression} requires a 6-field expression
 * ({@code seconds minutes hours day-of-month month day-of-week}). Users almost
 * universally write the standard 5-field form. We normalize the 5-field form by
 * prepending {@code "0 "} (run at second 0) and reject anything that still
 * fails to parse with a clear, actionable error message — the silent skip in
 * the schedule sync hook had been masking bad config for hours of debugging.
 */
public final class CronExpressions {

    private CronExpressions() {}

    /**
     * Returns the canonical 6-field form of the given expression, prepending
     * {@code "0 "} for 5-field input. Whitespace is collapsed but otherwise
     * the value is left unchanged — Spring's parser is the source of truth.
     *
     * @throws IllegalArgumentException if the input is null/blank or the
     *     normalized form is not a valid Spring cron expression
     */
    public static String normalize(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("cron expression is required");
        }
        String trimmed = expression.trim().replaceAll("\\s+", " ");
        int fieldCount = trimmed.split(" ").length;
        String canonical = switch (fieldCount) {
            case 5 -> "0 " + trimmed;
            case 6 -> trimmed;
            default -> throw new IllegalArgumentException(
                    "cron must be a 5- or 6-field Spring expression; got '" + expression + "'");
        };
        try {
            CronExpression.parse(canonical);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "cron must be a 6-field Spring expression (with seconds); got '"
                            + expression + "': " + e.getMessage(), e);
        }
        return canonical;
    }

    /**
     * Parses the expression after normalization. Equivalent to
     * {@code CronExpression.parse(normalize(expression))}.
     */
    public static CronExpression parse(String expression) {
        return CronExpression.parse(normalize(expression));
    }

    /**
     * Returns true if the expression is a valid 5- or 6-field cron.
     */
    public static boolean isValid(String expression) {
        try {
            normalize(expression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
