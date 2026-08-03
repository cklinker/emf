package io.kelta.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditText Tests")
class AuditTextTest {

    @Test
    @DisplayName("strips the line breaks that would forge a second audit record")
    void stripsLineBreaks() {
        // The public portal endpoints log the submitted email before anything has
        // validated it. The audit stream is line-oriented, so a newline lets the
        // caller append a record of their choosing to the log that is relied on
        // to establish what happened.
        String forged = "a@b.test\nsecurity_event=PORTAL_SIGNUP actor=victim@b.test result=success";

        assertThat(AuditText.sanitize(forged)).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("neutralises carriage returns and other control characters, keeping spaces")
    void stripsControlCharacters() {
        // Spaces survive — the audit format is space-separated key=value pairs,
        // so mangling them would corrupt every line while guarding nothing.
        String value = "a" + (char) 13 + (char) 10 + "b" + (char) 9 + "c d" + (char) 127 + "e";

        assertThat(AuditText.sanitize(value)).isEqualTo("a__b_c d_e");
    }

    @Test
    @DisplayName("leaves an ordinary address untouched")
    void leavesNormalValuesAlone() {
        // Sanitising must not make real audit lines unreadable.
        assertThat(AuditText.sanitize("someone+tag@example.co.uk"))
                .isEqualTo("someone+tag@example.co.uk");
    }

    @Test
    @DisplayName("bounds the length so one request cannot flood the log")
    void boundsLength() {
        assertThat(AuditText.sanitize("x".repeat(10_000))).hasSize(256);
    }

    @Test
    @DisplayName("renders an absent value rather than dropping the field")
    void rendersAbsentValues() {
        // A missing actor= would shift the line's shape and break parsing.
        assertThat(AuditText.sanitize(null)).isEqualTo("unknown");
        assertThat(AuditText.sanitize("   ")).isEqualTo("unknown");
    }
}
