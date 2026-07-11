package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.PortalTokens;
import io.kelta.worker.service.telehealth.AppointmentService;
import io.kelta.worker.service.telehealth.SlotService;
import io.kelta.worker.service.telehealth.VisitTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduling API (telehealth slice 4). {@code /api/telehealth/**} is a static
 * gateway route (API_ACCESS only) — authorization is in-controller, the chat
 * idiom: portal users act on themselves, providers on their own appointments;
 * user types are re-validated server-side.
 *
 * <p>{@code GET /visits/{token}} is a PUBLIC (unauthenticated) path: it
 * validates the signed visit token against the live appointment, mints a
 * single-use 15-minute portal login token, and redirects into the kelta-auth
 * magic-link verify — one click from the email into an authenticated portal
 * session.
 */
@RestController
@RequestMapping("/api/telehealth")
public class TelehealthController {

    private static final Logger log = LoggerFactory.getLogger(TelehealthController.class);

    private final AppointmentService appointmentService;
    private final SlotService slotService;
    private final VisitTokenService visitTokenService;
    private final UserIdResolver userIdResolver;
    private final JdbcTemplate jdbcTemplate;
    private final String authBaseUrl;

    public TelehealthController(AppointmentService appointmentService,
                                SlotService slotService,
                                VisitTokenService visitTokenService,
                                UserIdResolver userIdResolver,
                                JdbcTemplate jdbcTemplate,
                                @Value("${kelta.auth.issuer-uri:}") String authBaseUrl) {
        this.appointmentService = appointmentService;
        this.slotService = slotService;
        this.visitTokenService = visitTokenService;
        this.userIdResolver = userIdResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.authBaseUrl = authBaseUrl;
    }

    public record BookRequest(String providerId, String portalUserId, String start,
                              Integer durationMinutes, String visitType, String reason) {}

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providers(HttpServletRequest request) {
        String tenantId = requireTenant();
        actor(request, tenantId); // any authenticated user
        return ResponseEntity.ok(Map.of("data", appointmentService.providers(tenantId)));
    }

    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> slots(HttpServletRequest request,
                                                     @RequestParam String providerId,
                                                     @RequestParam String from,
                                                     @RequestParam String to,
                                                     @RequestParam(defaultValue = "30") int duration) {
        String tenantId = requireTenant();
        actor(request, tenantId);
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        if (toInstant.isAfter(fromInstant.plus(java.time.Duration.ofDays(62)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Range too large (max 62 days)");
        }
        List<SlotService.Slot> slots =
                slotService.slots(tenantId, providerId, fromInstant, toInstant, duration, Instant.now());
        return ResponseEntity.ok(Map.of("data", slots.stream()
                .map(slot -> Map.of("start", slot.start().toString(), "end", slot.end().toString()))
                .toList()));
    }

    @PostMapping("/appointments")
    public ResponseEntity<Map<String, Object>> book(HttpServletRequest request,
                                                    @RequestBody BookRequest body) {
        String tenantId = requireTenant();
        ChatService.ChatActor actor = actor(request, tenantId);
        Map<String, Object> appointment = appointmentService.book(tenantId, actor,
                body.providerId(), body.portalUserId(), parseInstant(body.start()),
                body.durationMinutes() == null ? 30 : body.durationMinutes(),
                body.visitType(), body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
    }

    @GetMapping("/appointments")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "mine") String view,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        String tenantId = requireTenant();
        ChatService.ChatActor actor = actor(request, tenantId);
        return ResponseEntity.ok(Map.of("data",
                appointmentService.list(tenantId, actor, view, page, size), "view", view));
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(HttpServletRequest request, @PathVariable String id) {
        String tenantId = requireTenant();
        return ResponseEntity.ok(appointmentService.cancel(tenantId, actor(request, tenantId), id));
    }

    @PostMapping("/appointments/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(HttpServletRequest request, @PathVariable String id,
                                                        @RequestParam(defaultValue = "false") boolean noShow) {
        String tenantId = requireTenant();
        return ResponseEntity.ok(appointmentService.complete(tenantId, actor(request, tenantId), id, noShow));
    }

    /**
     * PUBLIC visit-link landing (gateway unauthenticated path). Stateless HMAC
     * validation + live-appointment re-check, then a fresh single-use portal
     * login token and a redirect into the auth verify endpoint.
     */
    @GetMapping("/visits/{token}")
    public ResponseEntity<Void> visit(@PathVariable String token) {
        VisitTokenService.VisitClaim claim = visitTokenService.verify(token, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired visit link"));

        // The token binds ids; the ROW decides — a cancelled appointment kills the link.
        Integer live = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telehealth_appointment "
                        + "WHERE id = ? AND tenant_id = ? AND portal_user_id = ? "
                        + "AND status IN ('REQUESTED', 'CONFIRMED', 'COMPLETED')",
                Integer.class, claim.appointmentId(), claim.tenantId(), claim.portalUserId());
        if (live == null || live == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired visit link");
        }

        String rawLogin = PortalTokens.generate();
        jdbcTemplate.update(
                "INSERT INTO portal_login_token (id, tenant_id, user_id, token_hash, purpose, "
                        + "expires_at, created_at) VALUES (?, ?, ?, ?, 'PORTAL_LOGIN', ?, NOW())",
                UUID.randomUUID().toString(), claim.tenantId(), claim.portalUserId(),
                PortalTokens.sha256(rawLogin),
                Timestamp.from(Instant.now().plus(java.time.Duration.ofMinutes(15))));

        String base = authBaseUrl.endsWith("/") ? authBaseUrl.substring(0, authBaseUrl.length() - 1) : authBaseUrl;
        log.debug("Visit link accepted for appointment {}", claim.appointmentId());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, base + "/portal/login/verify?token=" + rawLogin)
                .build();
    }

    // ------------------------------------------------------------- Helpers

    private ChatService.ChatActor actor(HttpServletRequest request, String tenantId) {
        String identifier = request.getHeader("X-User-Id");
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        String userId = userIdResolver.resolve(identifier, tenantId);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unresolvable identity");
        }
        String userType = request.getHeader("X-User-Type");
        return new ChatService.ChatActor(userId, identifier,
                userType == null || userType.isBlank() ? "INTERNAL" : userType);
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO-8601 instant: " + value);
        }
    }
}
