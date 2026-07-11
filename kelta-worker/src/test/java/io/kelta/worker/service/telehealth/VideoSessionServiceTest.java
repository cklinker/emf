package io.kelta.worker.service.telehealth;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("VideoSessionService")
class VideoSessionServiceTest {

    private static final String TENANT = "t1";
    private static final ChatService.ChatActor PORTAL =
            new ChatService.ChatActor("u-portal", "pat@example.com", "PORTAL");
    private static final ChatService.ChatActor STRANGER =
            new ChatService.ChatActor("u-stranger", "who@example.com", "PORTAL");

    private JdbcTemplate jdbcTemplate;
    private QueryEngine queryEngine;
    private LiveKitTokenService liveKitTokenService;
    private TenantQuotaResolver quotaResolver;
    private ChatService chatService;
    private VideoSessionService service;
    private Map<String, Object> quotas;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        queryEngine = mock(QueryEngine.class);
        CollectionRegistry registry = mock(CollectionRegistry.class);
        when(registry.get(anyString())).thenReturn(mock(CollectionDefinition.class));
        liveKitTokenService = mock(LiveKitTokenService.class);
        when(liveKitTokenService.serverUrl()).thenReturn("wss://livekit.test");
        when(liveKitTokenService.mint(anyString(), anyString(), anyString(), any()))
                .thenReturn(new LiveKitTokenService.MintedToken("jwt-token",
                        Instant.parse("2026-07-13T17:00:00Z")));
        quotaResolver = mock(TenantQuotaResolver.class);
        quotas = new HashMap<>();
        quotas.put(TenantTierQuotas.KEY_TELEHEALTH_ENABLED, true);
        when(quotaResolver.resolve(TENANT)).thenReturn(quotas);
        when(quotaResolver.intQuota(TENANT, TenantTierQuotas.KEY_VIDEO_MINUTES_PER_MONTH))
                .thenReturn(3000);
        when(jdbcTemplate.queryForObject(contains("SUM(duration_seconds)"), eq(Long.class),
                eq(TENANT))).thenReturn(0L);
        chatService = mock(ChatService.class);
        service = new VideoSessionService(jdbcTemplate, queryEngine, registry,
                liveKitTokenService, quotaResolver, chatService);
    }

    private void stubAppointment(String providerId, String portalUserId, String status,
                                 Instant start, Instant end) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "appt-1");
        row.put("provider_id", providerId);
        row.put("portal_user_id", portalUserId);
        row.put("status", status);
        row.put("scheduled_start", Timestamp.from(start));
        row.put("scheduled_end", Timestamp.from(end));
        when(jdbcTemplate.queryForList(contains("FROM telehealth_appointment"),
                eq("appt-1"), eq(TENANT))).thenReturn(List.of(row));
    }

    @Test
    @DisplayName("mints a room-scoped token for an in-window participant, creating the session lazily")
    void happyPath() {
        Instant now = Instant.parse("2026-07-13T15:00:00Z");
        stubAppointment("u-provider", "u-portal", "CONFIRMED",
                now.minusSeconds(60), now.plusSeconds(1740));
        when(jdbcTemplate.queryForList(contains("FROM video_session"), eq(TENANT), eq("appt-1")))
                .thenReturn(List.of());
        when(queryEngine.create(any(), any())).thenAnswer(inv -> {
            Map<String, Object> data = inv.getArgument(1);
            Map<String, Object> created = new HashMap<>(data);
            created.put("id", "vs-1");
            return created;
        });

        VideoSessionService.VideoAccess access =
                service.appointmentToken(TENANT, PORTAL, "appt-1", now);

        assertThat(access.sessionId()).isEqualTo("vs-1");
        assertThat(access.roomName()).startsWith("t_" + TENANT + "_");
        assertThat(access.token()).isEqualTo("jwt-token");
        assertThat(access.url()).isEqualTo("wss://livekit.test");
    }

    @Test
    @DisplayName("403 for non-participants; 409 outside the window or on non-CONFIRMED status")
    void guards() {
        Instant now = Instant.parse("2026-07-13T15:00:00Z");
        stubAppointment("u-provider", "u-portal", "CONFIRMED",
                now.minusSeconds(60), now.plusSeconds(1740));
        assertStatus(() -> service.appointmentToken(TENANT, STRANGER, "appt-1", now),
                HttpStatus.FORBIDDEN);

        // Too early: 20 minutes before start (grace is 15).
        stubAppointment("u-provider", "u-portal", "CONFIRMED",
                now.plusSeconds(1200), now.plusSeconds(3000));
        assertStatus(() -> service.appointmentToken(TENANT, PORTAL, "appt-1", now),
                HttpStatus.CONFLICT);

        stubAppointment("u-provider", "u-portal", "CANCELLED",
                now.minusSeconds(60), now.plusSeconds(1740));
        assertStatus(() -> service.appointmentToken(TENANT, PORTAL, "appt-1", now),
                HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("fails closed when telehealth is disabled or the minute budget is spent")
    void featureAndGovernorGates() {
        quotas.put(TenantTierQuotas.KEY_TELEHEALTH_ENABLED, false);
        assertStatus(() -> service.appointmentToken(TENANT, PORTAL, "appt-1", Instant.now()),
                HttpStatus.FORBIDDEN);

        quotas.put(TenantTierQuotas.KEY_TELEHEALTH_ENABLED, true);
        when(jdbcTemplate.queryForObject(contains("SUM(duration_seconds)"), eq(Long.class),
                eq(TENANT))).thenReturn(3000L * 60);
        assertStatus(() -> service.appointmentToken(TENANT, PORTAL, "appt-1", Instant.now()),
                HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("conversation tokens require chat membership")
    void conversationMembership() {
        when(chatService.isMember(TENANT, "conv-1", "u-portal")).thenReturn(false);
        assertStatus(() -> service.conversationToken(TENANT, PORTAL, "conv-1"),
                HttpStatus.FORBIDDEN);

        when(chatService.isMember(TENANT, "conv-1", "u-portal")).thenReturn(true);
        when(queryEngine.create(any(), any())).thenAnswer(inv -> {
            Map<String, Object> created = new HashMap<>(inv.<Map<String, Object>>getArgument(1));
            created.put("id", "vs-2");
            return created;
        });
        VideoSessionService.VideoAccess access =
                service.conversationToken(TENANT, PORTAL, "conv-1");
        assertThat(access.sessionId()).isEqualTo("vs-2");
    }

    @Test
    @DisplayName("reuses the existing session for an appointment (unique-index contract)")
    void reusesExistingSession() {
        Instant now = Instant.parse("2026-07-13T15:00:00Z");
        stubAppointment("u-provider", "u-portal", "CONFIRMED",
                now.minusSeconds(60), now.plusSeconds(1740));
        when(jdbcTemplate.queryForList(contains("FROM video_session"), eq(TENANT), eq("appt-1")))
                .thenReturn(List.of(Map.of("id", "vs-existing", "room_name", "t_t1_r")));

        VideoSessionService.VideoAccess access =
                service.appointmentToken(TENANT, PORTAL, "appt-1", now);

        assertThat(access.sessionId()).isEqualTo("vs-existing");
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("records recording consent for a session member and updates the row")
    void recordsConsent() {
        when(jdbcTemplate.queryForList(contains("FROM video_session"), eq("vs-1"), eq(TENANT)))
                .thenReturn(List.of(sessionRow("appt-1", null)));
        when(jdbcTemplate.queryForList(contains("FROM telehealth_appointment"),
                eq("appt-1"), eq(TENANT), eq("u-portal"), eq("u-portal")))
                .thenReturn(List.of(Map.of("ok", 1)));

        service.updateRecordingConsent(TENANT, PORTAL, "vs-1", true);

        verify(queryEngine).update(any(), eq("vs-1"), eq(Map.of("recordingConsent", true)));
    }

    @Test
    @DisplayName("consent is member-only and 404s an unknown session")
    void consentGuards() {
        when(jdbcTemplate.queryForList(contains("FROM video_session"), eq("nope"), eq(TENANT)))
                .thenReturn(List.of());
        assertStatus(() -> service.updateRecordingConsent(TENANT, PORTAL, "nope", true),
                HttpStatus.NOT_FOUND);

        when(jdbcTemplate.queryForList(contains("FROM video_session"), eq("vs-1"), eq(TENANT)))
                .thenReturn(List.of(sessionRow("appt-1", null)));
        when(jdbcTemplate.queryForList(contains("FROM telehealth_appointment"),
                eq("appt-1"), eq(TENANT), eq("u-stranger"), eq("u-stranger")))
                .thenReturn(List.of());
        assertStatus(() -> service.updateRecordingConsent(TENANT, STRANGER, "vs-1", true),
                HttpStatus.FORBIDDEN);
        verify(queryEngine, never()).update(any(), anyString(), any());
    }

    private static Map<String, Object> sessionRow(String appointmentId, String conversationId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "vs-1");
        row.put("appointment_id", appointmentId);
        row.put("conversation_id", conversationId);
        return row;
    }

    private void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(expected));
    }
}
