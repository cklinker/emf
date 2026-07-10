package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.telehealth.LiveKitTokenService;
import io.kelta.worker.service.telehealth.LiveKitWebhookService;
import io.kelta.worker.service.telehealth.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * Video token minting + LiveKit webhook intake (telehealth slice 5).
 * Token endpoints ride the authenticated {@code /api/telehealth/**} static
 * route (in-controller authz, chat idiom); the webhook is a gateway
 * UNAUTHENTICATED path whose only trust anchor is the LiveKit JWT signature +
 * body digest — verified before anything is parsed as an event.
 */
@RestController
@RequestMapping("/api/telehealth")
public class VideoSessionController {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionController.class);

    private final VideoSessionService videoSessionService;
    private final LiveKitTokenService liveKitTokenService;
    private final LiveKitWebhookService liveKitWebhookService;
    private final UserIdResolver userIdResolver;

    public VideoSessionController(VideoSessionService videoSessionService,
                                  LiveKitTokenService liveKitTokenService,
                                  LiveKitWebhookService liveKitWebhookService,
                                  UserIdResolver userIdResolver) {
        this.videoSessionService = videoSessionService;
        this.liveKitTokenService = liveKitTokenService;
        this.liveKitWebhookService = liveKitWebhookService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping("/appointments/{id}/video-token")
    public ResponseEntity<Map<String, Object>> appointmentToken(HttpServletRequest request,
                                                                @PathVariable String id) {
        String tenantId = requireTenant();
        VideoSessionService.VideoAccess access = videoSessionService.appointmentToken(
                tenantId, actor(request, tenantId), id, Instant.now());
        return ResponseEntity.ok(toBody(access));
    }

    @PostMapping("/conversations/{id}/video-token")
    public ResponseEntity<Map<String, Object>> conversationToken(HttpServletRequest request,
                                                                 @PathVariable String id) {
        String tenantId = requireTenant();
        VideoSessionService.VideoAccess access = videoSessionService.conversationToken(
                tenantId, actor(request, tenantId), id);
        return ResponseEntity.ok(toBody(access));
    }

    /** UNAUTHENTICATED path — signature + digest verified before processing. */
    @PostMapping("/webhooks/livekit")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "Authorization", required = false)
                                        String authorization,
                                        @RequestBody String rawBody) {
        if (liveKitTokenService.verifyWebhook(authorization, rawBody).isEmpty()) {
            log.warn("Rejected LiveKit webhook with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        liveKitWebhookService.process(rawBody);
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> toBody(VideoSessionService.VideoAccess access) {
        return Map.of(
                "sessionId", access.sessionId(),
                "roomName", access.roomName(),
                "url", access.url(),
                "token", access.token(),
                "expiresAt", access.expiresAt().toString());
    }

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
}
