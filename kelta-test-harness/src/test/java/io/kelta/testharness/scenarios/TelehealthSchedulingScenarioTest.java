package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduling through the real stack (telehealth slice 4, V169): seed an
 * availability rule, read slots over the API, book one as an admin on behalf
 * of a freshly invited portal user, verify the double-book rejection (the
 * advisory-lock + slot-recheck path), the participant record_share, and the
 * cancel transition — the pieces Mockito can't reach.
 */
@DisplayName("Telehealth Scheduling Scenario")
class TelehealthSchedulingScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("slots → book → double-book 409 → cancel, with share + RLS rows persisted")
    @SuppressWarnings("unchecked")
    void schedulingRoundTrip() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/telehealth/providers",
                HttpStatus.OK, 20);

        // Portal user to book for.
        ResponseEntity<Map> invited = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/admin/users/portal-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "scheduling-scenario@example.com", "firstName", "Sched"))
                .retrieve().toEntity(Map.class);
        String portalUserId = (String) invited.getBody().get("userId");

        // The admin user acts as the provider — find their id, then seed a rule
        // covering tomorrow's weekday, 09:00–17:00 UTC.
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        int weekday = tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY ? 0
                : tomorrow.getDayOfWeek().getValue();
        String providerId;
        try (Connection conn = openDbConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM platform_user WHERE tenant_id = ? AND user_type = 'INTERNAL' "
                            + "AND status = 'ACTIVE' ORDER BY created_at LIMIT 1")) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    providerId = rs.getString("id");
                }
            }
        }

        // Seed the rule through the generic JSON:API route — the spec-designated
        // management path for availability. Regression for the V169 TIME-column
        // mismatch that failed every write here with a varchar/time bind error
        // (fixed by V172 + the STRING(8) field defs).
        Map<String, Object> ruleBody = Map.of("data", Map.of(
                "type", "telehealth-availability",
                "attributes", Map.of(
                        "providerId", providerId,
                        "kind", "RULE",
                        "weekday", weekday,
                        "startTime", "09:00",
                        "endTime", "17:00",
                        "timezone", "UTC",
                        "active", true)));
        ResponseEntity<Map> rule = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/telehealth-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ruleBody)
                .retrieve().toEntity(Map.class);
        assertThat(rule.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Slots for tomorrow are offered.
        Instant from = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = tomorrow.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        ResponseEntity<Map> slots = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/telehealth/slots?providerId=" + providerId
                        + "&from=" + from + "&to=" + to + "&duration=30")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> slotList = (List<Map<String, Object>>) slots.getBody().get("data");
        assertThat(slotList).isNotEmpty();
        String slotStart = (String) slotList.get(0).get("start");

        // Book it (staff booking on behalf of the portal user).
        ResponseEntity<Map> booked = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/telehealth/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("providerId", providerId, "portalUserId", portalUserId,
                        "start", slotStart, "durationMinutes", 30, "visitType", "Harness visit"))
                .retrieve().toEntity(Map.class);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String appointmentId = String.valueOf(booked.getBody().get("id"));

        // Same slot again → the recheck under the advisory lock rejects it.
        assertThat(postExpectingError(token, "/" + slug + "/api/telehealth/appointments",
                Map.of("providerId", providerId, "portalUserId", portalUserId,
                        "start", slotStart, "durationMinutes", 30)))
                .isEqualTo(409);

        try (Connection conn = openDbConnection()) {
            // Appointment row persisted with the tenant stamp (RLS predicate feed).
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tenant_id, status FROM telehealth_appointment WHERE id = ?")) {
                ps.setString(1, appointmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("tenant_id")).isEqualTo(tenantId);
                    assertThat(rs.getString("status")).isEqualTo("CONFIRMED");
                }
            }
            // The hook granted the portal user a participant share.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM record_share WHERE record_id = ? "
                            + "AND shared_with_id = ? AND reason = 'participant'")) {
                ps.setString(1, appointmentId);
                ps.setString(2, portalUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }

        // Provider cancels; slot frees up again.
        ResponseEntity<Map> cancelled = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/telehealth/appointments/" + appointmentId + "/cancel")
                .retrieve().toEntity(Map.class);
        assertThat(cancelled.getBody().get("status")).isEqualTo("CANCELLED");

        ResponseEntity<Map> slotsAfter = gatewayClientWithToken(token)
                .get().uri("/" + slug + "/api/telehealth/slots?providerId=" + providerId
                        + "&from=" + from + "&to=" + to + "&duration=30")
                .retrieve().toEntity(Map.class);
        List<Map<String, Object>> after = (List<Map<String, Object>>) slotsAfter.getBody().get("data");
        assertThat(after.stream().map(s -> s.get("start"))).contains(slotStart);
    }

    private int postExpectingError(String token, String uri, Map<String, ?> body) {
        try {
            return gatewayClientWithToken(token)
                    .post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().toEntity(Map.class)
                    .getStatusCode().value();
        } catch (org.springframework.web.client.HttpClientErrorException
                 | org.springframework.web.client.HttpServerErrorException e) {
            return e.getStatusCode().value();
        }
    }
}
