package io.kelta.worker.service.telehealth;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.email.DefaultEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AppointmentService")
class AppointmentServiceTest {

    private static final String TENANT = "t1";
    private static final ChatService.ChatActor PORTAL =
            new ChatService.ChatActor("u-portal", "pat@example.com", "PORTAL");
    private static final ChatService.ChatActor STAFF =
            new ChatService.ChatActor("u-provider", "dr@example.com", "INTERNAL");
    private static final Instant START = Instant.parse("2026-07-13T15:00:00Z");

    private JdbcTemplate jdbcTemplate;
    private QueryEngine queryEngine;
    private SlotService slotService;
    private VisitTokenService visitTokenService;
    private DefaultEmailService emailService;
    private EmailRepository emailRepository;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        queryEngine = mock(QueryEngine.class);
        CollectionRegistry registry = mock(CollectionRegistry.class);
        when(registry.get(anyString())).thenReturn(mock(CollectionDefinition.class));
        slotService = mock(SlotService.class);
        visitTokenService = mock(VisitTokenService.class);
        when(visitTokenService.sign(anyString(), anyString(), anyString(), any()))
                .thenReturn("signed-visit-token");
        emailService = mock(DefaultEmailService.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<DefaultEmailService> emailProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(emailProvider.getIfAvailable()).thenReturn(emailService);
        emailRepository = mock(EmailRepository.class);
        service = new AppointmentService(jdbcTemplate, queryEngine, registry, slotService,
                visitTokenService, emailProvider, emailRepository, "https://app.example.com");
    }

    private void stubUserType(String userId, String type) {
        when(jdbcTemplate.queryForList(contains("SELECT user_type"), eq(String.class),
                eq(userId), eq(TENANT))).thenReturn(type == null ? List.of() : List.of(type));
    }

    @Test
    @DisplayName("books a slot the SlotService actually offers, forcing the portal actor as subject")
    void booksOfferedSlot() {
        stubUserType("u-portal", "PORTAL");
        stubUserType("u-provider", "INTERNAL");
        when(slotService.slots(eq(TENANT), eq("u-provider"), eq(START), any(), eq(30), any()))
                .thenReturn(List.of(new SlotService.Slot(START, START.plusSeconds(1800))));
        when(queryEngine.create(any(), any())).thenReturn(Map.of(
                "id", "appt-1", "portalUserId", "u-portal", "providerId", "u-provider",
                "scheduledStart", START.toString(),
                "scheduledEnd", START.plusSeconds(1800).toString()));
        // Confirmation email path: template missing → skipped gracefully.
        when(emailRepository.findTemplateByKey(TENANT, "appointment.confirmed"))
                .thenReturn(Optional.empty());

        Map<String, Object> appointment = service.book(TENANT, PORTAL, "u-provider",
                "someone-else-ignored", START, 30, "Checkup", "knee");

        assertThat(appointment.get("id")).isEqualTo("appt-1");
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).create(any(), data.capture());
        assertThat(data.getValue())
                .containsEntry("portalUserId", "u-portal")     // actor wins, param ignored
                .containsEntry("status", "CONFIRMED")
                .containsEntry("providerId", "u-provider");
        // Advisory lock taken before the slot re-check.
        verify(jdbcTemplate).queryForObject(contains("pg_advisory_xact_lock"),
                eq(Object.class), eq("u-provider"));
    }

    @Test
    @DisplayName("409 when the requested time is not an offered slot")
    void rejectsUnofferedSlot() {
        stubUserType("u-portal", "PORTAL");
        stubUserType("u-provider", "INTERNAL");
        when(slotService.slots(anyString(), anyString(), any(), any(), anyInt(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.book(TENANT, PORTAL, "u-provider", null,
                START, 30, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("rejects non-portal subjects and non-internal providers")
    void validatesUserTypes() {
        stubUserType("u-portal", "PORTAL");
        stubUserType("u-provider", "PORTAL"); // provider is not INTERNAL
        assertThatThrownBy(() -> service.book(TENANT, PORTAL, "u-provider", null,
                START, 30, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        stubUserType("u-staff2", "INTERNAL");
        stubUserType("u-provider", "INTERNAL");
        assertThatThrownBy(() -> service.book(TENANT, STAFF, "u-staff2", "u-staff2",
                START, 30, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("cancel is owner-scoped and refuses terminal states; complete is staff-only")
    void lifecycleGuards() {
        when(queryEngine.getById(any(), eq("appt-1"))).thenReturn(Optional.of(Map.of(
                "id", "appt-1", "portalUserId", "u-portal", "providerId", "u-provider",
                "status", "COMPLETED")));
        assertThatThrownBy(() -> service.cancel(TENANT, PORTAL, "appt-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        when(queryEngine.getById(any(), eq("appt-2"))).thenReturn(Optional.of(Map.of(
                "id", "appt-2", "portalUserId", "someone-else", "providerId", "u-provider",
                "status", "CONFIRMED")));
        assertThatThrownBy(() -> service.cancel(TENANT, PORTAL, "appt-2"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.complete(TENANT, PORTAL, "appt-2", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("list validates views and gates provider view to staff")
    void listGuards() {
        assertThatThrownBy(() -> service.list(TENANT, STAFF, "everything", 0, 50))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.list(TENANT, PORTAL, "provider", 0, 50))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
