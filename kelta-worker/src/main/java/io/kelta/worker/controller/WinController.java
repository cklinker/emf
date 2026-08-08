package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.repository.WinRepository;
import io.kelta.worker.repository.WinRepository.Win;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Member-facing win API + the live-wins ticker feed (consumer-alerting slice 9).
 *
 * <p>{@code /api/wins/**} is a {@code static-} gateway route, so the gateway applies only the
 * blanket {@code API_ACCESS} check — <b>all</b> scoping is enforced here. Writes go through
 * {@link QueryEngine} rather than straight to the repository so {@code WinGuardHook} (owner
 * guard) fires; the generic dynamic route reaches the same collection, which is why that hook
 * exists — this controller is the pleasant door, not the only one.
 *
 * <p>{@code GET /recent} is the deliberately cross-member ticker: it exposes ONLY opt-in
 * {@code isPublic} wins and ONLY the redacted fields a ticker needs (first-name claimant label,
 * summary, category, quantity, time) — never {@code memberId} or any other member identity.
 * Anonymous access to the ticker belongs with the public read-surface slice; for now the feed
 * is authenticated like any other API.
 */
@RestController
@RequestMapping("/api/wins")
public class WinController {

    private static final Logger log = LoggerFactory.getLogger(WinController.class);

    static final String COLLECTION = "wins";
    private static final int MAX_SUMMARY = 280;
    private static final int DEFAULT_RECENT = 20;

    private final WinRepository winRepository;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final UserIdResolver userIdResolver;

    public WinController(WinRepository winRepository,
                        QueryEngine queryEngine,
                        CollectionRegistry collectionRegistry,
                        UserIdResolver userIdResolver) {
        this.winRepository = winRepository;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.userIdResolver = userIdResolver;
    }

    /** Records a win owned by the caller ("I got the spot"). */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateWinRequest body,
                                                      HttpServletRequest request) {
        String tenantId = requireTenant();
        String subject = requireActor(request, tenantId);

        if (body == null || body.summary() == null || body.summary().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "summary is required");
        }
        String summary = body.summary().trim();
        if (summary.length() > MAX_SUMMARY) {
            summary = summary.substring(0, MAX_SUMMARY);
        }
        boolean isPublic = Boolean.TRUE.equals(body.isPublic());

        Map<String, Object> data = new LinkedHashMap<>();
        // Direct queryEngine.create must set tenantId itself — the JSON:API layer injects it on
        // the HTTP path, and without it the NOT NULL is violated.
        data.put("tenantId", tenantId);
        data.put("memberId", subject);
        data.put("summary", summary);
        data.put("isPublic", isPublic);
        // First name only — the sole identity fragment ever shown on the public ticker.
        winRepository.findMemberDisplayFirstName(tenantId, subject)
                .ifPresent(name -> data.put("claimantName", name));
        putIfPresent(data, "targetId", body.targetId());
        putIfPresent(data, "watchId", body.watchId());
        putIfPresent(data, "alertId", body.alertId());
        putIfPresent(data, "category", body.category());
        if (body.quantity() != null) {
            data.put("quantity", body.quantity());
        }
        if (body.claimedAt() != null) {
            data.put("claimedAt", body.claimedAt());
        }

        Map<String, Object> created = queryEngine.create(definition(), data);
        log.info("Member {} recorded a win in tenant {} (public={})", subject, tenantId, isPublic);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
    }

    /** The caller's own wins, newest first. */
    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        String tenantId = requireTenant();
        String subject = requireActor(request, tenantId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Win win : winRepository.findByMember(tenantId, subject)) {
            items.add(ownWin(win));
        }
        return Map.of("data", items);
    }

    /**
     * The live-wins ticker: recent opt-in-public wins, redacted. Cross-member by design (social
     * proof), but only public rows and only ticker-safe fields — no member identity beyond the
     * first-name claimant label.
     */
    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "20") int limit) {
        String tenantId = requireTenant();
        int effective = limit <= 0 ? DEFAULT_RECENT : limit;

        List<Map<String, Object>> items = new ArrayList<>();
        for (Win win : winRepository.findRecentPublic(tenantId, effective)) {
            items.add(tickerWin(win));
        }
        return Map.of("data", items);
    }

    /** Per-target success stats (count + last win time). Aggregate only. */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam String targetId) {
        String tenantId = requireTenant();
        if (targetId == null || targetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetId is required");
        }
        WinRepository.TargetStats s = winRepository.statsForTarget(tenantId, targetId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", s.targetId());
        data.put("winCount", s.winCount());
        data.put("lastWinAt", s.lastWinAt());
        return Map.of("data", data);
    }

    // ------------------------------------------------------------- Mapping

    /** The owner's full view of their own win. */
    private Map<String, Object> ownWin(Win win) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", win.id());
        item.put("targetId", win.targetId());
        item.put("watchId", win.watchId());
        item.put("alertId", win.alertId());
        item.put("category", win.category());
        item.put("summary", win.summary());
        item.put("quantity", win.quantity());
        item.put("isPublic", win.isPublic());
        item.put("claimedAt", win.claimedAt());
        return item;
    }

    /** The redacted, ticker-safe projection — no member identity beyond the first name. */
    private Map<String, Object> tickerWin(Win win) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("claimantName", win.claimantName());
        item.put("category", win.category());
        item.put("summary", win.summary());
        item.put("quantity", win.quantity());
        item.put("claimedAt", win.claimedAt());
        return item;
    }

    // ------------------------------------------------------------- Helpers

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private CollectionDefinition definition() {
        CollectionDefinition definition = collectionRegistry.get(COLLECTION);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "wins collection is not registered");
        }
        return definition;
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private String requireActor(HttpServletRequest request, String tenantId) {
        String identifier = request.getHeader("X-User-Id");
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        String userId = userIdResolver.resolve(identifier, tenantId);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unresolvable identity");
        }
        return userId;
    }

    /** Request body for {@code POST /api/wins}. */
    public record CreateWinRequest(String targetId, String watchId, String alertId,
                                   String category, String summary, Integer quantity,
                                   Boolean isPublic, String claimedAt) {
    }
}
