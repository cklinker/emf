package io.kelta.worker.service.telehealth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider self-service availability (telehealth). A provider reads and rewrites
 * their OWN weekly schedule (RULE rows) and date exceptions (EXCEPTION rows:
 * whole-day closures or extra windows) — the same {@code telehealth_availability}
 * rows {@link SlotService} expands into bookable slots.
 *
 * <p>Ownership is enforced by the caller (the controller passes the authenticated
 * user's id as {@code providerId}); the request body never carries a provider id,
 * so a provider can only ever edit their own schedule. {@link #replaceForProvider}
 * is a transactional full-replace: it deletes the provider's rows and re-inserts
 * the validated set, so the UI can load → edit → save the whole schedule.
 *
 * <p>All I/O is plain {@code Map}/{@code List} (JSON-shaped) so nothing here needs
 * native reflection registration.
 */
@Service
public class AvailabilityService {

    /** weekday is stored Sunday=0..Saturday=6 (matches SlotService's mod-7 expansion). */
    private static final int MIN_WEEKDAY = 0;
    private static final int MAX_WEEKDAY = 6;
    private static final String DEFAULT_TIMEZONE = "Europe/Lisbon";

    private final JdbcTemplate jdbcTemplate;

    public AvailabilityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The provider's current schedule as {timezone, rules[], exceptions[]}. */
    public Map<String, Object> getForProvider(String tenantId, String providerId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT kind, weekday, exception_date, start_time, end_time, timezone, closed
                FROM telehealth_availability
                WHERE tenant_id = ? AND provider_id = ? AND active = true
                ORDER BY weekday NULLS LAST, exception_date NULLS LAST, start_time
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("kind", rs.getString("kind"));
                    row.put("weekday", rs.getObject("weekday") == null ? null : rs.getInt("weekday"));
                    row.put("exceptionDate", rs.getString("exception_date"));
                    row.put("startTime", rs.getString("start_time"));
                    row.put("endTime", rs.getString("end_time"));
                    row.put("timezone", rs.getString("timezone"));
                    row.put("closed", rs.getBoolean("closed"));
                    return row;
                },
                tenantId, providerId);

        List<Map<String, Object>> rules = new ArrayList<>();
        List<Map<String, Object>> exceptions = new ArrayList<>();
        String timezone = DEFAULT_TIMEZONE;
        for (Map<String, Object> row : rows) {
            timezone = (String) row.getOrDefault("timezone", timezone);
            if ("EXCEPTION".equals(row.get("kind"))) {
                exceptions.add(Map.of(
                        "exceptionDate", nullToEmpty(row.get("exceptionDate")),
                        "closed", row.get("closed"),
                        "startTime", nullToEmpty(row.get("startTime")),
                        "endTime", nullToEmpty(row.get("endTime"))));
            } else {
                rules.add(Map.of(
                        "weekday", row.get("weekday"),
                        "startTime", nullToEmpty(row.get("startTime")),
                        "endTime", nullToEmpty(row.get("endTime"))));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timezone", timezone);
        out.put("rules", rules);
        out.put("exceptions", exceptions);
        return out;
    }

    /**
     * Validates and replaces the provider's entire schedule. Rejects malformed
     * input with 400 before touching any row (validate-all-then-write).
     */
    @Transactional
    public Map<String, Object> replaceForProvider(String tenantId, String providerId,
                                                  String actor, Map<String, Object> body) {
        String timezone = validateTimezone(str(body.get("timezone"), DEFAULT_TIMEZONE));
        List<Map<String, Object>> rules = asList(body.get("rules"));
        List<Map<String, Object>> exceptions = asList(body.get("exceptions"));

        // Validate everything up front.
        for (Map<String, Object> rule : rules) {
            int weekday = weekday(rule.get("weekday"));
            LocalTime start = time(rule.get("startTime"), "rule startTime");
            LocalTime end = time(rule.get("endTime"), "rule endTime");
            requireOrdered(start, end, "rule");
            if (weekday < MIN_WEEKDAY || weekday > MAX_WEEKDAY) {
                throw badRequest("weekday must be 0 (Sunday) through 6 (Saturday)");
            }
        }
        for (Map<String, Object> ex : exceptions) {
            date(ex.get("exceptionDate"));
            if (!bool(ex.get("closed"))) {
                LocalTime start = time(ex.get("startTime"), "exception startTime");
                LocalTime end = time(ex.get("endTime"), "exception endTime");
                requireOrdered(start, end, "exception");
            }
        }

        jdbcTemplate.update(
                "DELETE FROM telehealth_availability WHERE tenant_id = ? AND provider_id = ?",
                tenantId, providerId);

        for (Map<String, Object> rule : rules) {
            insert(tenantId, providerId, actor, "RULE",
                    weekday(rule.get("weekday")), null, timezone,
                    normalize(rule.get("startTime")), normalize(rule.get("endTime")), false);
        }
        for (Map<String, Object> ex : exceptions) {
            boolean closed = bool(ex.get("closed"));
            insert(tenantId, providerId, actor, "EXCEPTION",
                    null, date(ex.get("exceptionDate")).toString(), timezone,
                    closed ? null : normalize(ex.get("startTime")),
                    closed ? null : normalize(ex.get("endTime")), closed);
        }
        return getForProvider(tenantId, providerId);
    }

    private void insert(String tenantId, String providerId, String actor, String kind,
                        Integer weekday, String exceptionDate, String timezone,
                        String startTime, String endTime, boolean closed) {
        jdbcTemplate.update(
                """
                INSERT INTO telehealth_availability
                    (id, tenant_id, provider_id, kind, weekday, exception_date,
                     start_time, end_time, timezone, closed, active, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?::date, ?, ?, ?, ?, true, ?, ?)
                """,
                UUID.randomUUID().toString(), tenantId, providerId, kind, weekday, exceptionDate,
                startTime, endTime, timezone, closed, actor, actor);
    }

    // ------------------------------------------------------------- Validation

    private static int weekday(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw badRequest("weekday is required and must be a number 0-6");
    }

    private static LocalTime time(Object value, String label) {
        String s = value == null ? "" : value.toString().trim();
        if (s.isEmpty()) {
            throw badRequest(label + " is required (HH:mm)");
        }
        try {
            return LocalTime.parse(s);
        } catch (Exception e) {
            throw badRequest(label + " must be HH:mm — got '" + s + "'");
        }
    }

    private static LocalDate date(Object value) {
        String s = value == null ? "" : value.toString().trim();
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw badRequest("exceptionDate must be YYYY-MM-DD — got '" + s + "'");
        }
    }

    private static void requireOrdered(LocalTime start, LocalTime end, String label) {
        if (!end.isAfter(start)) {
            throw badRequest(label + " endTime must be after startTime");
        }
    }

    private static String validateTimezone(String tz) {
        try {
            return ZoneId.of(tz).getId();
        } catch (Exception e) {
            throw badRequest("invalid timezone: " + tz);
        }
    }

    private static String normalize(Object value) {
        return LocalTime.parse(value.toString().trim()).toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        throw badRequest("expected a JSON array");
    }

    private static String str(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
