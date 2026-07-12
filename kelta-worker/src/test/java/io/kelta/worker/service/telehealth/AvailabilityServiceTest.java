package io.kelta.worker.service.telehealth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilityServiceTest {

    private static final String TENANT = "t1";
    private static final String PROVIDER = "u-provider";
    private static final String ACTOR = "provider@example.com";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AvailabilityService service = new AvailabilityService(jdbc);

    private Map<String, Object> rule(Object weekday, String start, String end) {
        return Map.of("weekday", weekday, "startTime", start, "endTime", end);
    }

    private void replace(Map<String, Object> body) {
        service.replaceForProvider(TENANT, PROVIDER, ACTOR, body);
    }

    private void assertBadRequest(Map<String, Object> body) {
        assertThatThrownBy(() -> replace(body))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        // Validation happens before any write — nothing is deleted or inserted.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsWeekdayOutOfRange() {
        assertBadRequest(Map.of("rules", List.of(rule(7, "09:00", "12:00"))));
    }

    @Test
    void rejectsMalformedTime() {
        assertBadRequest(Map.of("rules", List.of(rule(1, "9am", "12:00"))));
    }

    @Test
    void rejectsEndNotAfterStart() {
        assertBadRequest(Map.of("rules", List.of(rule(1, "12:00", "12:00"))));
    }

    @Test
    void rejectsBadTimezone() {
        assertBadRequest(Map.of("timezone", "Mars/Olympus", "rules", List.of()));
    }

    @Test
    void rejectsMalformedExceptionDate() {
        assertBadRequest(Map.of("exceptions",
                List.of(Map.of("exceptionDate", "15-08-2026", "closed", true))));
    }

    @Test
    void rejectsOpenExceptionWithoutWindow() {
        assertBadRequest(Map.of("exceptions",
                List.of(Map.of("exceptionDate", "2026-08-15", "closed", false))));
    }

    @Test
    void replacesWithValidScheduleAndForcesProviderId() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq(TENANT), eq(PROVIDER))).thenReturn(List.of());

        replace(Map.of(
                "timezone", "Europe/Lisbon",
                "rules", List.of(rule(1, "09:00", "13:00"), rule(1, "14:00", "18:00")),
                "exceptions", List.of(
                        Map.of("exceptionDate", "2026-08-15", "closed", true),
                        Map.of("exceptionDate", "2026-08-20", "closed", false,
                                "startTime", "10:00", "endTime", "12:00"))));

        // One delete scoped to the provider, then one insert per row (2 rules + 2 exceptions).
        verify(jdbc).update(argThat(sql -> sql.startsWith("DELETE")), eq(TENANT), eq(PROVIDER));
        verify(jdbc, times(4)).update(argThat(sql -> sql.contains("INSERT INTO telehealth_availability")),
                any(Object[].class));
    }
}
