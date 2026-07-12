package io.kelta.worker.service.telehealth;

import io.kelta.worker.service.telehealth.SlotService.AvailabilityRow;
import io.kelta.worker.service.telehealth.SlotService.Busy;
import io.kelta.worker.service.telehealth.SlotService.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlotService.computeSlots")
class SlotServiceTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");

    private static AvailabilityRow rule(int weekday, String start, String end, ZoneId zone) {
        return new AvailabilityRow("RULE", weekday, null,
                LocalTime.parse(start), LocalTime.parse(end), zone, false);
    }

    private static AvailabilityRow closedException(LocalDate date, ZoneId zone) {
        return new AvailabilityRow("EXCEPTION", null, date, null, null, zone, true);
    }

    private static AvailabilityRow extraWindow(LocalDate date, String start, String end, ZoneId zone) {
        return new AvailabilityRow("EXCEPTION", null, date,
                LocalTime.parse(start), LocalTime.parse(end), zone, false);
    }

    @Test
    @DisplayName("expands a weekly rule into duration-sized future slots inside the range")
    void expandsWeeklyRule() {
        // Monday 2026-07-13, 09:00–10:30 Denver → three 30-minute slots.
        Instant from = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, DENVER).toInstant();
        Instant to = ZonedDateTime.of(2026, 7, 14, 0, 0, 0, 0, DENVER).toInstant();

        List<Slot> slots = SlotService.computeSlots(
                List.of(rule(1, "09:00", "10:30", DENVER)), List.of(),
                from, to, 30, from.minusSeconds(1));

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).start())
                .isEqualTo(ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, DENVER).toInstant());
        assertThat(slots.get(2).end())
                .isEqualTo(ZonedDateTime.of(2026, 7, 13, 10, 30, 0, 0, DENVER).toInstant());
    }

    @Test
    @DisplayName("rules stay wall-clock-stable across the DST spring-forward transition")
    void dstSpringForward() {
        // US DST springs forward on Sunday 2026-03-08 in Denver. A Sunday
        // (weekday 0) 09:00–10:00 rule must still yield a 09:00 LOCAL slot.
        Instant from = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, DENVER).toInstant();
        Instant to = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, DENVER).toInstant();

        List<Slot> slots = SlotService.computeSlots(
                List.of(rule(0, "09:00", "10:00", DENVER)), List.of(),
                from, to, 60, from.minusSeconds(1));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).start().atZone(DENVER).toLocalTime())
                .isEqualTo(LocalTime.of(9, 0));
        // 09:00 MDT (UTC-6), not MST — the instant reflects the new offset.
        assertThat(slots.get(0).start())
                .isEqualTo(Instant.parse("2026-03-08T15:00:00Z"));
    }

    @Test
    @DisplayName("closed exceptions remove the whole day; extra windows add slots")
    void exceptionsApply() {
        Instant from = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, DENVER).toInstant();
        Instant to = ZonedDateTime.of(2026, 7, 15, 0, 0, 0, 0, DENVER).toInstant();

        List<Slot> slots = SlotService.computeSlots(
                List.of(
                        rule(1, "09:00", "10:00", DENVER),                     // Monday the 13th
                        closedException(LocalDate.of(2026, 7, 13), DENVER),    // …but closed
                        extraWindow(LocalDate.of(2026, 7, 14), "13:00", "14:00", DENVER)), // Tuesday extra
                List.of(),
                from, to, 60, from.minusSeconds(1));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).start().atZone(DENVER).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @Test
    @DisplayName("booked appointments and past times are removed")
    void busyAndPastRemoved() {
        Instant from = ZonedDateTime.of(2026, 7, 13, 0, 0, 0, 0, DENVER).toInstant();
        Instant to = ZonedDateTime.of(2026, 7, 14, 0, 0, 0, 0, DENVER).toInstant();
        Instant nineDenver = ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, DENVER).toInstant();

        List<Slot> slots = SlotService.computeSlots(
                List.of(rule(1, "09:00", "10:30", DENVER)),
                List.of(new Busy(nineDenver.plusSeconds(1800), nineDenver.plusSeconds(3600))), // 09:30–10:00 busy
                from, to, 30,
                nineDenver.plusSeconds(60)); // "now" is 09:01 → the 09:00 slot is past

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).start()).isEqualTo(nineDenver.plusSeconds(3600)); // only 10:00
    }

    @Test
    @DisplayName("guards: zero duration or inverted range yield nothing")
    void guards() {
        Instant from = Instant.parse("2026-07-13T00:00:00Z");
        assertThat(SlotService.computeSlots(List.of(), List.of(), from, from.plusSeconds(3600), 0, from))
                .isEmpty();
        assertThat(SlotService.computeSlots(List.of(), List.of(), from, from.minusSeconds(1), 30, from))
                .isEmpty();
    }

    @Test
    @DisplayName("parseTime: varchar(8) column values in both HH:mm and HH:mm:ss forms")
    void parseTimeHandlesStoredForms() {
        assertThat(SlotService.parseTime("09:00")).isEqualTo(LocalTime.of(9, 0));
        assertThat(SlotService.parseTime("17:00:00")).isEqualTo(LocalTime.of(17, 0));
        assertThat(SlotService.parseTime(null)).isNull();
        assertThat(SlotService.parseTime("")).isNull();
    }
}
