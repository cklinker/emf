package io.kelta.worker.service.telehealth;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.email.DefaultEmailService;
import io.kelta.worker.service.email.EmailAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Appointment lifecycle (telehealth slice 4). Booking is race-safe without DB
 * extensions: inside one transaction it takes a per-provider
 * {@code pg_advisory_xact_lock}, re-verifies the requested slot against the
 * live availability + bookings (never trusts a client-supplied time blindly),
 * then writes through {@link QueryEngine} so the appointment hook (participant
 * share) and record.changed (flows/audit/search) fire. Confirmation email
 * carries the signed visit link + an RFC 5545 .ics attachment; the reminder
 * sweep and cancellation reuse the same template path.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final Duration VISIT_LINK_GRACE = Duration.ofHours(1);
    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy HH:mm (zzz)", Locale.ENGLISH);

    private final JdbcTemplate jdbcTemplate;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final SlotService slotService;
    private final VisitTokenService visitTokenService;
    private final DefaultEmailService emailService;
    private final EmailRepository emailRepository;
    private final String workerPublicBaseUrl;

    public AppointmentService(JdbcTemplate jdbcTemplate,
                              QueryEngine queryEngine,
                              CollectionRegistry collectionRegistry,
                              SlotService slotService,
                              VisitTokenService visitTokenService,
                              DefaultEmailService emailService,
                              EmailRepository emailRepository,
                              @Value("${kelta.external-base-url:}") String externalBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.slotService = slotService;
        this.visitTokenService = visitTokenService;
        this.emailService = emailService;
        this.emailRepository = emailRepository;
        this.workerPublicBaseUrl = externalBaseUrl;
    }

    // ------------------------------------------------------------- Booking

    @Transactional
    public Map<String, Object> book(String tenantId, ChatService.ChatActor actor,
                                    String providerId, String portalUserId,
                                    Instant start, int durationMinutes,
                                    String visitType, String reason) {
        if (providerId == null || providerId.isBlank() || start == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId and start are required");
        }
        int duration = durationMinutes <= 0 ? 30 : Math.min(durationMinutes, 240);
        Instant end = start.plus(Duration.ofMinutes(duration));
        String subject = actor.isPortal() ? actor.userId() : portalUserId;
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portalUserId is required");
        }
        requireUserType(tenantId, subject, "PORTAL", "portalUserId must be a portal user");
        requireUserType(tenantId, providerId, "INTERNAL", "providerId must be an internal user");

        // Serialize bookings per provider for the rest of this transaction —
        // the overlap check below is then race-free without btree_gist.
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, providerId);

        boolean offered = slotService
                .slots(tenantId, providerId, start, end, duration, Instant.now())
                .stream()
                .anyMatch(slot -> slot.start().equals(start) && slot.end().equals(end));
        if (!offered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The requested time is not an available slot");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("providerId", providerId);
        data.put("portalUserId", subject);
        data.put("scheduledStart", start.toString());
        data.put("scheduledEnd", end.toString());
        data.put("status", "CONFIRMED");
        if (visitType != null && !visitType.isBlank()) {
            data.put("visitType", visitType.length() > 100 ? visitType.substring(0, 100) : visitType);
        }
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason.length() > 500 ? reason.substring(0, 500) : reason);
        }
        Map<String, Object> appointment = queryEngine.create(definition(), data);

        sendConfirmation(tenantId, appointment);
        return appointment;
    }

    @Transactional
    public Map<String, Object> cancel(String tenantId, ChatService.ChatActor actor, String appointmentId) {
        Map<String, Object> appointment = loadOwned(tenantId, actor, appointmentId);
        String status = String.valueOf(appointment.get("status"));
        if ("CANCELLED".equals(status) || "COMPLETED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is already " + status);
        }
        Map<String, Object> updated = queryEngine.update(definition(), appointmentId,
                        Map.of("status", "CANCELLED", "cancelledAt", Instant.now().toString()))
                .orElseThrow(this::notFound);
        sendTemplate(tenantId, updated, "appointment.cancelled", null);
        return updated;
    }

    @Transactional
    public Map<String, Object> complete(String tenantId, ChatService.ChatActor actor,
                                        String appointmentId, boolean noShow) {
        if (actor.isPortal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
        }
        loadOwned(tenantId, actor, appointmentId);
        return queryEngine.update(definition(), appointmentId,
                        Map.of("status", noShow ? "NO_SHOW" : "COMPLETED"))
                .orElseThrow(this::notFound);
    }

    // ------------------------------------------------------------- Reads

    public List<Map<String, Object>> list(String tenantId, ChatService.ChatActor actor,
                                          String view, int page, int size) {
        int limit = Math.min(Math.max(1, size), 100);
        int offset = Math.max(0, page) * limit;
        String filterColumn = switch (view) {
            case "mine" -> "portal_user_id";
            case "provider" -> "provider_id";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "view must be mine or provider");
        };
        if ("provider".equals(view) && actor.isPortal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider view is staff-only");
        }
        return jdbcTemplate.query(
                "SELECT id, provider_id, portal_user_id, scheduled_start, scheduled_end, status, "
                        + "visit_type, reason FROM telehealth_appointment "
                        + "WHERE tenant_id = ? AND " + filterColumn + " = ? "
                        + "ORDER BY scheduled_start DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("providerId", rs.getString("provider_id"));
                    row.put("portalUserId", rs.getString("portal_user_id"));
                    row.put("scheduledStart", rs.getTimestamp("scheduled_start").toInstant().toString());
                    row.put("scheduledEnd", rs.getTimestamp("scheduled_end").toInstant().toString());
                    row.put("status", rs.getString("status"));
                    row.put("visitType", rs.getString("visit_type"));
                    row.put("reason", rs.getString("reason"));
                    return row;
                },
                tenantId, actor.userId(), limit, offset);
    }

    /** Providers = internal users with at least one active availability rule. */
    public List<Map<String, Object>> providers(String tenantId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT pu.id, COALESCE(NULLIF(TRIM(pu.first_name || ' ' || pu.last_name), ''), pu.email) AS name
                FROM telehealth_availability a
                JOIN platform_user pu ON pu.id = a.provider_id
                WHERE a.tenant_id = ? AND a.active = true AND pu.status = 'ACTIVE'
                ORDER BY name
                """,
                (rs, i) -> Map.<String, Object>of("id", rs.getString("id"), "name", rs.getString("name")),
                tenantId);
    }

    // ------------------------------------------------------------- Emails

    void sendConfirmation(String tenantId, Map<String, Object> appointment) {
        String appointmentId = String.valueOf(appointment.get("id"));
        Instant start = Instant.parse(String.valueOf(appointment.get("scheduledStart")));
        Instant end = Instant.parse(String.valueOf(appointment.get("scheduledEnd")));

        Map<String, Object> vars = templateVars(tenantId, appointment);
        Optional<Map<String, Object>> template =
                emailRepository.findTemplateByKey(tenantId, "appointment.confirmed");
        Recipient recipient = recipient(tenantId, String.valueOf(appointment.get("portalUserId")));
        if (template.isEmpty() || recipient == null) {
            log.warn("Skipping confirmation email for appointment {} (template or recipient missing)",
                    appointmentId);
            return;
        }
        String subject = DefaultEmailService.substitute(
                (String) template.get().get("subject"), vars);
        String body = DefaultEmailService.substitute(
                (String) template.get().get("body_html"), vars);
        String ics = IcsGenerator.appointmentInvite(appointmentId, start, end,
                String.valueOf(vars.getOrDefault("visitType", "Telehealth visit")),
                "Join: " + vars.get("visitLink"));
        emailService.queueEmailWithAttachments(tenantId, recipient.email(), subject, body,
                "APPOINTMENT_CONFIRMED", appointmentId,
                List.of(new EmailAttachment("appointment.ics", "text/calendar",
                        ics.getBytes(StandardCharsets.UTF_8))));
    }

    /** Shared by the reminder sweep — template send with the visit link, no attachment. */
    public void sendTemplate(String tenantId, Map<String, Object> appointment,
                             String templateKey, String sourceOverride) {
        Recipient recipient = recipient(tenantId, String.valueOf(appointment.get("portalUserId")));
        if (recipient == null) {
            return;
        }
        emailService.sendByKey(tenantId, recipient.email(), templateKey,
                templateVars(tenantId, appointment),
                sourceOverride != null ? sourceOverride : templateKey.toUpperCase(Locale.ROOT).replace('.', '_'),
                String.valueOf(appointment.get("id")));
    }

    Map<String, Object> templateVars(String tenantId, Map<String, Object> appointment) {
        String appointmentId = String.valueOf(appointment.get("id"));
        String portalUserId = String.valueOf(appointment.get("portalUserId"));
        Instant start = Instant.parse(String.valueOf(appointment.get("scheduledStart")));
        Instant end = Instant.parse(String.valueOf(appointment.get("scheduledEnd")));

        Recipient recipient = recipient(tenantId, portalUserId);
        String providerName = jdbcTemplate.queryForList(
                        "SELECT COALESCE(NULLIF(TRIM(first_name || ' ' || last_name), ''), email) AS n "
                                + "FROM platform_user WHERE id = ?", String.class,
                        String.valueOf(appointment.get("providerId"))).stream()
                .findFirst().orElse("your provider");

        String token = visitTokenService.sign(tenantId, appointmentId, portalUserId,
                end.plus(VISIT_LINK_GRACE));
        String base = workerPublicBaseUrl.endsWith("/")
                ? workerPublicBaseUrl.substring(0, workerPublicBaseUrl.length() - 1)
                : workerPublicBaseUrl;
        String visitLink = base + "/api/telehealth/visits/" + token;

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("firstName", recipient == null || recipient.firstName() == null ? "" : recipient.firstName());
        vars.put("providerName", providerName);
        vars.put("visitType", Optional.ofNullable(appointment.get("visitType"))
                .map(String::valueOf).filter(s -> !s.isBlank() && !"null".equals(s))
                .orElse("Telehealth visit"));
        vars.put("startsAtLabel", LABEL.format(start.atZone(ZoneId.of("UTC"))));
        vars.put("visitLink", visitLink);
        return vars;
    }

    // ------------------------------------------------------------- Helpers

    private record Recipient(String email, String firstName) {}

    private Recipient recipient(String tenantId, String userId) {
        return jdbcTemplate.query(
                        "SELECT email, first_name FROM platform_user WHERE id = ? AND tenant_id = ?",
                        (rs, i) -> new Recipient(rs.getString("email"), rs.getString("first_name")),
                        userId, tenantId).stream()
                .findFirst().orElse(null);
    }

    private void requireUserType(String tenantId, String userId, String expectedType, String message) {
        String type = jdbcTemplate.queryForList(
                        "SELECT user_type FROM platform_user WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
                        String.class, userId, tenantId).stream()
                .findFirst().orElse(null);
        if (!expectedType.equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    /** Portal users may act on their own appointments; staff on theirs (as provider). */
    private Map<String, Object> loadOwned(String tenantId, ChatService.ChatActor actor,
                                          String appointmentId) {
        Map<String, Object> appointment = queryEngine.getById(definition(), appointmentId)
                .orElseThrow(this::notFound);
        String owner = actor.isPortal() ? "portalUserId" : "providerId";
        if (!actor.userId().equals(String.valueOf(appointment.get(owner)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your appointment");
        }
        return appointment;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
    }

    private CollectionDefinition definition() {
        CollectionDefinition definition = collectionRegistry.get("telehealth-appointments");
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "telehealth-appointments collection not registered");
        }
        return definition;
    }
}
