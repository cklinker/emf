package io.kelta.worker.controller;

import io.kelta.worker.service.analytics.AnalyticsCaptureService;
import io.kelta.worker.service.analytics.AnalyticsCaptureService.IncomingEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Authenticated ingest for client-emitted analytics events (consumer-alerting slice 8):
 * page views, assistant questions, acquisition/UTM. The route gets only the blanket
 * {@code API_ACCESS} check at the gateway, which is exactly right — every event is owner-stamped
 * from the gateway {@code X-User-Id}, so a caller can only ever write their own rows and any
 * {@code memberId}/{@code tenantId} in the body is ignored.
 *
 * <p>Anonymous (unauthenticated) ingest is intentionally NOT offered here — a public write
 * surface belongs with the public-traffic hardening slice (its per-IP budget + bot challenge are
 * the prerequisites). Until then capture is authenticated + server-side only.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsIngestController {

    private final AnalyticsCaptureService captureService;

    public AnalyticsIngestController(AnalyticsCaptureService captureService) {
        this.captureService = captureService;
    }

    /** One event from the client; identity/tenant/geo are never trusted from this body. */
    public record EventDto(
            String eventType,
            String query,
            Boolean zeroResult,
            String matchedTargetId,
            String path,
            String referrer,
            Map<String, Object> utm,
            String sessionId,
            Map<String, Object> metadata,
            Instant occurredAt) {
    }

    public record IngestRequest(List<EventDto> events) {
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody IngestRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String memberId) {

        List<EventDto> events = request == null ? null : request.events();
        if (events == null || events.isEmpty()) {
            return ResponseEntity.accepted().body(Map.of("accepted", 0));
        }
        if (events.size() > AnalyticsCaptureService.MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "events batch exceeds the " + AnalyticsCaptureService.MAX_BATCH + "-event limit");
        }

        List<IncomingEvent> incoming = events.stream()
                .map(e -> new IncomingEvent(
                        e.eventType(), e.query(), e.zeroResult(), e.matchedTargetId(),
                        e.path(), e.referrer(), e.utm(), e.sessionId(), e.metadata(), e.occurredAt()))
                .toList();

        int accepted = captureService.capture(incoming, memberId);
        return ResponseEntity.accepted().body(Map.of("accepted", accepted));
    }
}
