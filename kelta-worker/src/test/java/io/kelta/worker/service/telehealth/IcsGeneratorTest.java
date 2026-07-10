package io.kelta.worker.service.telehealth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IcsGenerator")
class IcsGeneratorTest {

    @Test
    @DisplayName("emits a well-formed single-VEVENT calendar with UTC times and CRLF endings")
    void wellFormed() {
        String ics = IcsGenerator.appointmentInvite("appt-1",
                Instant.parse("2026-07-13T15:00:00Z"), Instant.parse("2026-07-13T15:30:00Z"),
                "Telehealth visit", "Join: https://example/visit");

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).contains("BEGIN:VEVENT");
        assertThat(ics).contains("UID:appt-1@kelta");
        assertThat(ics).contains("DTSTART:20260713T150000Z");
        assertThat(ics).contains("DTEND:20260713T153000Z");
        assertThat(ics).contains("SUMMARY:Telehealth visit");
        assertThat(ics).contains("STATUS:CONFIRMED");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        // Every line CRLF-terminated (RFC 5545 §3.1).
        assertThat(ics.split("\r\n")).allSatisfy(line -> assertThat(line).doesNotContain("\n"));
    }

    @Test
    @DisplayName("escapes TEXT per RFC 5545 §3.3.11")
    void escapes() {
        assertThat(IcsGenerator.escape("a,b;c\\d\ne")).isEqualTo("a\\,b\\;c\\\\d\\ne");
    }
}
