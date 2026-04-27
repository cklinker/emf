package io.kelta.mcp.observe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatRedactionFilterTest {

    private final PatRedactionFilter filter = new PatRedactionFilter();

    private LoggingEvent event(String message) {
        LoggingEvent e = new LoggingEvent();
        e.setMessage(message);
        e.setLevel(Level.INFO);
        return e;
    }

    @Test
    void allowsNormalMessages() {
        assertThat(filter.decide(event("created collection projects")))
                .isEqualTo(FilterReply.NEUTRAL);
        assertThat(filter.decide(event("user submitted 17 records"))).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void deniesEventsContainingPatToken() {
        assertThat(filter.decide(event("auth failed for klt_AbCdEf12345")))
                .isEqualTo(FilterReply.DENY);
        assertThat(filter.decide(event("cached entry: klt_xyz987"))).isEqualTo(FilterReply.DENY);
    }

    @Test
    void doesNotDenyMessagesContainingKltAsPlainWord() {
        assertThat(filter.decide(event("Welcome to klt platform"))).isEqualTo(FilterReply.NEUTRAL);
        assertThat(filter.decide(event("klt_ alone is not a token"))).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void survivesNullEvent() {
        assertThat(filter.decide(null)).isEqualTo(FilterReply.NEUTRAL);
    }
}
