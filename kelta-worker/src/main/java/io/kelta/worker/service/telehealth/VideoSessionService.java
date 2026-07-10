package io.kelta.worker.service.telehealth;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.SecurityAuditLogger;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Video session lifecycle (telehealth slice 5). Sessions are created LAZILY at
 * the first token request — one per appointment (unique index guards the
 * race), or ad-hoc per chat conversation. Token minting is the enforcement
 * point: appointment/conversation membership, the join window
 * (start−15min … end+60min), the per-tenant {@code telehealthEnabled} gate,
 * and the {@code videoMinutesPerMonth} governor (summed from ended sessions —
 * no extra counter infrastructure) all check here, fail closed.
 */
@Service
public class VideoSessionService {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionService.class);
    static final Duration JOIN_EARLY_GRACE = Duration.ofMinutes(15);
    static final Duration JOIN_LATE_GRACE = Duration.ofMinutes(60);

    public record VideoAccess(String sessionId, String roomName, String url,
                              String token, Instant expiresAt) {}

    private final JdbcTemplate jdbcTemplate;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final LiveKitTokenService liveKitTokenService;
    private final TenantQuotaResolver quotaResolver;
    private final ChatService chatService;

    public VideoSessionService(JdbcTemplate jdbcTemplate,
                               QueryEngine queryEngine,
                               CollectionRegistry collectionRegistry,
                               LiveKitTokenService liveKitTokenService,
                               TenantQuotaResolver quotaResolver,
                               ChatService chatService) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.liveKitTokenService = liveKitTokenService;
        this.quotaResolver = quotaResolver;
        this.chatService = chatService;
    }

    /** Token for a scheduled appointment's room (provider or the appointment's portal user). */
    public VideoAccess appointmentToken(String tenantId, ChatService.ChatActor actor,
                                        String appointmentId, Instant now) {
        requireTelehealthEnabled(tenantId);
        requireMinutesBudget(tenantId);

        Map<String, Object> appointment = jdbcTemplate.queryForList(
                        "SELECT id, provider_id, portal_user_id, scheduled_start, scheduled_end, status "
                                + "FROM telehealth_appointment WHERE id = ? AND tenant_id = ?",
                        appointmentId, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        boolean member = actor.userId().equals(String.valueOf(appointment.get("provider_id")))
                || actor.userId().equals(String.valueOf(appointment.get("portal_user_id")));
        if (!member) {
            SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_ACCESS_DENIED,
                    actor.email(), appointmentId, tenantId, "failure", "video token — not a participant");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your appointment");
        }
        String status = String.valueOf(appointment.get("status"));
        if (!"CONFIRMED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is " + status);
        }
        Instant start = ((java.sql.Timestamp) appointment.get("scheduled_start")).toInstant();
        Instant end = ((java.sql.Timestamp) appointment.get("scheduled_end")).toInstant();
        if (now.isBefore(start.minus(JOIN_EARLY_GRACE)) || now.isAfter(end.plus(JOIN_LATE_GRACE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Outside the visit window");
        }

        Map<String, Object> session = getOrCreateSession(tenantId, appointmentId, null);
        return mint(tenantId, actor, session, end.plus(JOIN_LATE_GRACE));
    }

    /** Ad-hoc room for a chat conversation (participants only, escalation from chat). */
    public VideoAccess conversationToken(String tenantId, ChatService.ChatActor actor,
                                         String conversationId) {
        requireTelehealthEnabled(tenantId);
        requireMinutesBudget(tenantId);
        if (!chatService.isMember(tenantId, conversationId, actor.userId())) {
            SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_ACCESS_DENIED,
                    actor.email(), conversationId, tenantId, "failure", "video token — not a participant");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a conversation participant");
        }
        Map<String, Object> session = getOrCreateSession(tenantId, null, conversationId);
        return mint(tenantId, actor, session, null);
    }

    // ------------------------------------------------------------- Internals

    private VideoAccess mint(String tenantId, ChatService.ChatActor actor,
                             Map<String, Object> session, Instant expiresAt) {
        String sessionId = String.valueOf(session.get("id"));
        String roomName = String.valueOf(session.get("roomName"));
        LiveKitTokenService.MintedToken minted =
                liveKitTokenService.mint(actor.userId(), actor.email(), roomName, expiresAt);
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.VIDEO_TOKEN_ISSUED,
                actor.email(), sessionId, tenantId, "success", "room=" + roomName);
        return new VideoAccess(sessionId, roomName, liveKitTokenService.serverUrl(),
                minted.token(), minted.expiresAt());
    }

    private Map<String, Object> getOrCreateSession(String tenantId, String appointmentId,
                                                   String conversationId) {
        if (appointmentId != null) {
            Map<String, Object> existing = findByAppointment(tenantId, appointmentId);
            if (existing != null) {
                return existing;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        if (appointmentId != null) {
            data.put("appointmentId", appointmentId);
        }
        if (conversationId != null) {
            data.put("conversationId", conversationId);
        }
        // Opaque, tenant-namespaced room; the token is room-scoped so a leaked
        // token can never reach another tenant's room.
        data.put("roomName", "t_" + tenantId + "_" + UUID.randomUUID());
        data.put("status", "CREATED");
        try {
            return queryEngine.create(definition(), data);
        } catch (RuntimeException e) {
            // Unique appointment index lost the race — the other caller's row wins.
            if (appointmentId != null) {
                Map<String, Object> existing = findByAppointment(tenantId, appointmentId);
                if (existing != null) {
                    return existing;
                }
            }
            throw e;
        }
    }

    private Map<String, Object> findByAppointment(String tenantId, String appointmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, room_name FROM video_session WHERE tenant_id = ? AND appointment_id = ?",
                tenantId, appointmentId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", rows.get(0).get("id"));
        session.put("roomName", rows.get(0).get("room_name"));
        return session;
    }

    void requireTelehealthEnabled(String tenantId) {
        Object enabled = quotaResolver.resolve(tenantId)
                .get(TenantTierQuotas.KEY_TELEHEALTH_ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Telehealth video is not enabled for this tenant");
        }
    }

    void requireMinutesBudget(String tenantId) {
        int limitMinutes = quotaResolver.intQuota(tenantId, TenantTierQuotas.KEY_VIDEO_MINUTES_PER_MONTH);
        if (limitMinutes == Integer.MAX_VALUE) {
            return;
        }
        Long usedSeconds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) FROM video_session "
                        + "WHERE tenant_id = ? AND ended_at >= date_trunc('month', NOW())",
                Long.class, tenantId);
        long usedMinutes = usedSeconds == null ? 0 : usedSeconds / 60;
        if (usedMinutes >= limitMinutes) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Video minute budget exhausted (" + limitMinutes + "/month). "
                            + "Raise videoMinutesPerMonth in governor limits.");
        }
    }

    private CollectionDefinition definition() {
        CollectionDefinition definition = collectionRegistry.get("video-sessions");
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "video-sessions collection not registered");
        }
        return definition;
    }
}
