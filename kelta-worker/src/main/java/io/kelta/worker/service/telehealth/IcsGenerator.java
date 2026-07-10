package io.kelta.worker.service.telehealth;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Minimal RFC 5545 calendar generation for appointment confirmations
 * (telehealth slice 4). One VEVENT, UTC times, METHOD:PUBLISH — enough for
 * every mainstream calendar client to render an invite from an email
 * attachment. Deliberately no external dependency.
 */
public final class IcsGenerator {

    private static final DateTimeFormatter ICS_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsGenerator() {
    }

    public static String appointmentInvite(String appointmentId, Instant start, Instant end,
                                           String summary, String description) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Kelta//Telehealth//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + appointmentId + "@kelta");
        line(ics, "DTSTAMP:" + ICS_UTC.format(Instant.now()));
        line(ics, "DTSTART:" + ICS_UTC.format(start));
        line(ics, "DTEND:" + ICS_UTC.format(end));
        line(ics, "SUMMARY:" + escape(summary));
        if (description != null && !description.isBlank()) {
            line(ics, "DESCRIPTION:" + escape(description));
        }
        line(ics, "STATUS:CONFIRMED");
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /** RFC 5545 §3.3.11 TEXT escaping. */
    static String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /** RFC 5545 §3.1: CRLF line endings; fold lines longer than 75 octets. */
    private static void line(StringBuilder ics, String content) {
        String remaining = content;
        boolean first = true;
        while (remaining.length() > 74) {
            ics.append(first ? "" : " ").append(remaining, 0, 74).append("\r\n");
            remaining = remaining.substring(74);
            first = false;
        }
        ics.append(first ? "" : " ").append(remaining).append("\r\n");
    }
}
