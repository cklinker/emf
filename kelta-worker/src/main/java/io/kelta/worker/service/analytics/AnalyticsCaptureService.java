package io.kelta.worker.service.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.runtime.context.GeoContext;
import io.kelta.runtime.context.GeoStamp;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.AnalyticsEventRepository;
import io.kelta.worker.repository.AnalyticsEventRepository.AnalyticsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Captures the questions a consumer product's users ask, plus lightweight usage/acquisition
 * events, into {@code analytics_event} (consumer-alerting slice 8).
 *
 * <p><b>Best-effort by contract.</b> A dropped analytics row is strictly better than a failed or
 * slowed search, so every write is swallowed on error and logged at {@code debug} — nothing here
 * ever propagates into the product response. Each capture runs in its OWN transaction
 * ({@link Propagation#REQUIRES_NEW}) so a capture failure can neither poison the caller's
 * transaction nor be poisoned by it.
 *
 * <p>Tenant, member, and coarse geo are read from the request context ({@link TenantContext} /
 * the gateway-stamped {@code X-User-Id} passed in by the controller / {@link GeoContext}) — never
 * trusted from the event body. Geo is country + region only; city and coordinates are
 * deliberately never captured.
 */
@Service
public class AnalyticsCaptureService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCaptureService.class);

    /** Batch cap enforced by the ingest controller; here as the shared source of truth. */
    public static final int MAX_BATCH = 100;

    static final int MAX_EVENT_TYPE_LEN = 30;
    static final int MAX_QUERY_LEN = 2000;
    static final int MAX_URL_LEN = 500;
    static final int MAX_SESSION_LEN = 64;
    static final int MAX_ID_LEN = 36;
    static final int MAX_REGION_LEN = 80;

    private final AnalyticsEventRepository repository;
    private final ObjectMapper objectMapper;

    public AnalyticsCaptureService(AnalyticsEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * A single event as received from the ingest endpoint. {@code memberId}/{@code tenantId} are
     * intentionally absent — they are stamped server-side, never trusted from the client.
     */
    public record IncomingEvent(
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

    /**
     * Server-side capture of one search query (best-effort). {@code memberId} is the
     * gateway-stamped caller; may be null.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captureSearch(String query, boolean zeroResult, String matchedTargetId, String memberId) {
        if (query == null || query.isBlank()) {
            return;
        }
        IncomingEvent event = new IncomingEvent(
                "SEARCH_QUERY", query, zeroResult, matchedTargetId,
                null, null, null, null, null, null);
        captureInternal(List.of(event), memberId);
    }

    /**
     * Authenticated ingest of a client-emitted batch (best-effort). Returns the number of rows
     * accepted after clamping/validation. Callers must have already enforced {@link #MAX_BATCH}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int capture(List<IncomingEvent> events, String memberId) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        return captureInternal(events, memberId);
    }

    private int captureInternal(List<IncomingEvent> incoming, String memberId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            log.debug("analytics capture skipped: no tenant in context");
            return 0;
        }
        String geoCountry = emptyToNull(GeoContext.currentCountry());
        String geoRegion = clamp(GeoContext.current().map(GeoStamp::region).orElse(null), MAX_REGION_LEN);
        Instant now = Instant.now();

        List<AnalyticsEvent> rows = new ArrayList<>(incoming.size());
        for (IncomingEvent e : incoming) {
            String type = clamp(e.eventType(), MAX_EVENT_TYPE_LEN);
            if (type == null || type.isBlank()) {
                // event_type is the one hard requirement; skip a malformed row rather than fail
                // the batch — telemetry is lossy by nature.
                continue;
            }
            Instant occurred = (e.occurredAt() == null || e.occurredAt().isAfter(now)) ? now : e.occurredAt();
            rows.add(new AnalyticsEvent(
                    type,
                    clamp(e.query(), MAX_QUERY_LEN),
                    e.zeroResult(),
                    clamp(e.matchedTargetId(), MAX_ID_LEN),
                    clamp(e.path(), MAX_URL_LEN),
                    clamp(e.referrer(), MAX_URL_LEN),
                    toJson(e.utm()),
                    clamp(e.sessionId(), MAX_SESSION_LEN),
                    memberId,
                    geoCountry,
                    geoRegion,
                    toJson(e.metadata()),
                    occurred));
        }
        if (rows.isEmpty()) {
            return 0;
        }
        try {
            repository.insertAll(tenantId, memberId, rows);
            return rows.size();
        } catch (RuntimeException ex) {
            log.debug("analytics capture insert failed ({} row(s)): {}", rows.size(), ex.toString());
            return 0;
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }

    private static String clamp(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
