package io.kelta.worker.service.telehealth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free/busy slot computation (telehealth slice 4). Weekly RULE rows expand in
 * the rule's OWN timezone (so a 09:00–17:00 rule stays 09:00–17:00 across DST
 * transitions), date EXCEPTION rows either close a whole day (closed=true) or
 * add an extra window for that date, and anything overlapping an active
 * (REQUESTED/CONFIRMED) appointment or lying in the past is removed. The math
 * is a pure function ({@link #computeSlots}) so DST behavior is unit-testable
 * without a database.
 */
@Service
public class SlotService {

    public record AvailabilityRow(String kind, Integer weekday, LocalDate exceptionDate,
                                  LocalTime startTime, LocalTime endTime, ZoneId zone,
                                  boolean closed) {}

    public record Busy(Instant start, Instant end) {}

    public record Slot(Instant start, Instant end) {}

    private final JdbcTemplate jdbcTemplate;

    public SlotService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Slot> slots(String tenantId, String providerId, Instant from, Instant to,
                            int durationMinutes, Instant now) {
        List<AvailabilityRow> availability = loadAvailability(tenantId, providerId);
        List<Busy> busy = loadBusy(tenantId, providerId, from, to);
        return computeSlots(availability, busy, from, to, durationMinutes, now);
    }

    /** Pure slot expansion — see class doc. */
    public static List<Slot> computeSlots(List<AvailabilityRow> availability, List<Busy> busy,
                                          Instant from, Instant to, int durationMinutes,
                                          Instant now) {
        if (durationMinutes <= 0 || !to.isAfter(from)) {
            return List.of();
        }
        Duration duration = Duration.ofMinutes(durationMinutes);

        List<AvailabilityRow> rules = availability.stream()
                .filter(row -> "RULE".equals(row.kind())).toList();
        Map<LocalDate, List<AvailabilityRow>> exceptionsByDate = new HashMap<>();
        for (AvailabilityRow row : availability) {
            if ("EXCEPTION".equals(row.kind()) && row.exceptionDate() != null) {
                exceptionsByDate.computeIfAbsent(row.exceptionDate(), d -> new ArrayList<>()).add(row);
            }
        }

        // Candidate windows per calendar date, keyed in each rule's zone.
        Map<Instant, Slot> ordered = new LinkedHashMap<>();
        for (AvailabilityRow rule : rules) {
            ZoneId zone = rule.zone();
            LocalDate day = from.atZone(zone).toLocalDate().minusDays(1); // DST safety margin
            LocalDate lastDay = to.atZone(zone).toLocalDate().plusDays(1);
            while (!day.isAfter(lastDay)) {
                LocalDate date = day;
                day = day.plusDays(1);
                if (date.getDayOfWeek().getValue() % 7 != rule.weekday() % 7) {
                    // ISO Monday=1..Sunday=7 → store Sunday=0..Saturday=6; normalize both mod 7.
                    continue;
                }
                List<AvailabilityRow> dayExceptions = exceptionsByDate.getOrDefault(date, List.of());
                if (dayExceptions.stream().anyMatch(AvailabilityRow::closed)) {
                    continue;
                }
                addWindow(ordered, zone, date, rule.startTime(), rule.endTime(),
                        duration, from, to, now, busy);
            }
        }
        // Additive exception windows (closed=false with a time range).
        for (Map.Entry<LocalDate, List<AvailabilityRow>> entry : exceptionsByDate.entrySet()) {
            for (AvailabilityRow exception : entry.getValue()) {
                if (!exception.closed() && exception.startTime() != null && exception.endTime() != null) {
                    addWindow(ordered, exception.zone(), entry.getKey(),
                            exception.startTime(), exception.endTime(),
                            duration, from, to, now, busy);
                }
            }
        }

        return ordered.values().stream()
                .sorted((a, b) -> a.start().compareTo(b.start()))
                .toList();
    }

    private static void addWindow(Map<Instant, Slot> ordered, ZoneId zone, LocalDate date,
                                  LocalTime startTime, LocalTime endTime, Duration duration,
                                  Instant from, Instant to, Instant now, List<Busy> busy) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return;
        }
        ZonedDateTime windowStart = date.atTime(startTime).atZone(zone);
        ZonedDateTime windowEnd = date.atTime(endTime).atZone(zone);
        Instant cursor = windowStart.toInstant();
        Instant end = windowEnd.toInstant();
        while (!cursor.plus(duration).isAfter(end)) {
            Instant slotEnd = cursor.plus(duration);
            boolean inRange = !cursor.isBefore(from) && !slotEnd.isAfter(to);
            boolean future = cursor.isAfter(now);
            if (inRange && future && !overlapsAny(cursor, slotEnd, busy)) {
                ordered.putIfAbsent(cursor, new Slot(cursor, slotEnd));
            }
            cursor = slotEnd;
        }
    }

    private static boolean overlapsAny(Instant start, Instant end, List<Busy> busy) {
        for (Busy b : busy) {
            if (start.isBefore(b.end()) && b.start().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    List<AvailabilityRow> loadAvailability(String tenantId, String providerId) {
        return jdbcTemplate.query(
                """
                SELECT kind, weekday, exception_date, start_time, end_time, timezone, closed
                FROM telehealth_availability
                WHERE tenant_id = ? AND provider_id = ? AND active = true
                """,
                (rs, i) -> new AvailabilityRow(
                        rs.getString("kind"),
                        rs.getObject("weekday") == null ? null : rs.getInt("weekday"),
                        rs.getObject("exception_date", LocalDate.class),
                        rs.getObject("start_time", LocalTime.class),
                        rs.getObject("end_time", LocalTime.class),
                        ZoneId.of(rs.getString("timezone")),
                        rs.getBoolean("closed")),
                tenantId, providerId);
    }

    List<Busy> loadBusy(String tenantId, String providerId, Instant from, Instant to) {
        return jdbcTemplate.query(
                """
                SELECT scheduled_start, scheduled_end FROM telehealth_appointment
                WHERE tenant_id = ? AND provider_id = ?
                  AND status IN ('REQUESTED', 'CONFIRMED')
                  AND scheduled_start < ? AND scheduled_end > ?
                """,
                (rs, i) -> new Busy(
                        rs.getTimestamp("scheduled_start").toInstant(),
                        rs.getTimestamp("scheduled_end").toInstant()),
                tenantId, providerId,
                java.sql.Timestamp.from(to), java.sql.Timestamp.from(from));
    }
}
