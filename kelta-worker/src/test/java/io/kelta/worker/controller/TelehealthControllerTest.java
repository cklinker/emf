package io.kelta.worker.controller;

import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.service.telehealth.AppointmentService;
import io.kelta.worker.service.telehealth.AvailabilityService;
import io.kelta.worker.service.telehealth.SlotService;
import io.kelta.worker.service.telehealth.VisitTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TelehealthController — public visit links")
class TelehealthControllerTest {

    private static final String TENANT = "t1";
    private static final String APPOINTMENT = "a0b1c2d3-0000-0000-0000-000000000001";
    private static final String PORTAL_USER = "pu-1";

    private VisitTokenService visitTokenService;
    private JdbcTemplate jdbcTemplate;
    private TelehealthController controller;

    @BeforeEach
    void setUp() {
        visitTokenService = mock(VisitTokenService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new TelehealthController(mock(AppointmentService.class), mock(SlotService.class),
                mock(AvailabilityService.class), visitTokenService, mock(UserIdResolver.class),
                jdbcTemplate, "https://auth.example.com/");
    }

    private void givenLiveAppointment() {
        when(visitTokenService.verify(eq("signed"), any(Instant.class))).thenReturn(
                Optional.of(new VisitTokenService.VisitClaim(TENANT, APPOINTMENT, PORTAL_USER,
                        Instant.now().plusSeconds(600))));
        when(jdbcTemplate.queryForObject(contains("FROM telehealth_appointment"), eq(Integer.class),
                eq(APPOINTMENT), eq(TENANT), eq(PORTAL_USER))).thenReturn(1);
    }

    private void givenPortalCallback(List<String> values) {
        when(jdbcTemplate.queryForList(contains("inviteRedirectUri"), eq(String.class), eq(TENANT)))
                .thenReturn(values);
    }

    @Test
    @DisplayName("redirects to the tenant's headless portal callback with the appointment id")
    void redirectsToHeadlessPortalCallback() {
        givenLiveAppointment();
        givenPortalCallback(List.of("https://portal.example.com/auth/callback"));

        ResponseEntity<Void> response = controller.visit("signed");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://portal.example.com/auth/callback?token=");
        assertThat(location).endsWith("&appointmentId=" + APPOINTMENT);
    }

    @Test
    @DisplayName("appends with & when the callback already carries a query string")
    void appendsToExistingQueryString() {
        givenLiveAppointment();
        givenPortalCallback(List.of("https://portal.example.com/auth/callback?src=email"));

        String location = controller.visit("signed").getHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location).startsWith("https://portal.example.com/auth/callback?src=email&token=");
    }

    @Test
    @DisplayName("falls back to the auth verify page when no headless callback is configured")
    void fallsBackToAuthVerifyWhenUnset() {
        givenLiveAppointment();
        givenPortalCallback(List.of());

        String location = controller.visit("signed").getHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location).startsWith("https://auth.example.com/portal/login/verify?token=");
        assertThat(location).doesNotContain("appointmentId");
    }

    @Test
    @DisplayName("treats a blank configured callback as unset")
    void fallsBackToAuthVerifyWhenBlank() {
        givenLiveAppointment();
        givenPortalCallback(List.of(" "));

        String location = controller.visit("signed").getHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location).startsWith("https://auth.example.com/portal/login/verify?token=");
    }

    @Test
    @DisplayName("404s when the appointment row is no longer live")
    void rejectsWhenAppointmentNotLive() {
        when(visitTokenService.verify(eq("signed"), any(Instant.class))).thenReturn(
                Optional.of(new VisitTokenService.VisitClaim(TENANT, APPOINTMENT, PORTAL_USER,
                        Instant.now().plusSeconds(600))));
        when(jdbcTemplate.queryForObject(contains("FROM telehealth_appointment"), eq(Integer.class),
                eq(APPOINTMENT), eq(TENANT), eq(PORTAL_USER))).thenReturn(0);

        assertThatThrownBy(() -> controller.visit("signed"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired visit link");
    }

    @Test
    @DisplayName("404s on an invalid or expired signed token")
    void rejectsInvalidToken() {
        when(visitTokenService.verify(eq("bad"), any(Instant.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.visit("bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired visit link");
    }
}
