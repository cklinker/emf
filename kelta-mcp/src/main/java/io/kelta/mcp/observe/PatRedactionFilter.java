package io.kelta.mcp.observe;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.regex.Pattern;

/**
 * Defense-in-depth Logback filter that drops any log event whose
 * formatted message contains a {@code klt_*} substring.
 *
 * <p>The application code already redacts PATs in error responses
 * (see {@link io.kelta.mcp.error.McpErrorMapper#redact}) and never
 * logs the token directly. This filter is a backstop: if any
 * regression slips through and a log line ever contains a PAT, the
 * filter denies the event so it never reaches the JSON appender.
 *
 * <p>Wired up in {@code logback-spring.xml} as a top-level filter on
 * the CONSOLE appender.
 */
public final class PatRedactionFilter extends Filter<ILoggingEvent> {

    private static final Pattern PAT_PATTERN = Pattern.compile("klt_[A-Za-z0-9]+");

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) return FilterReply.NEUTRAL;
        String message = event.getFormattedMessage();
        if (message != null && PAT_PATTERN.matcher(message).find()) {
            return FilterReply.DENY;
        }
        if (event.getThrowableProxy() != null) {
            String tm = event.getThrowableProxy().getMessage();
            if (tm != null && PAT_PATTERN.matcher(tm).find()) {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
