package io.kelta.gateway.websocket;

import io.kelta.runtime.messaging.nats.NatsConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ephemeral presence over the realtime socket (app-intelligence slice 3): tracks who
 * is viewing a resource (e.g. {@code record:orders/123}) fleet-wide and pushes
 * {@code presence.changed} snapshots to co-present LOCAL sessions.
 *
 * <p>Cross-pod state rides NATS deltas on {@code kelta.presence.<tenantId>}
 * (broadcast-consumed; new short-retention KELTA_PRESENCE stream): every local
 * join/leave publishes, a 30s heartbeat re-announces local sessions, and entries
 * expire after 90s — covering pod and session death without coordination. Payloads
 * are plain Maps end-to-end (no typed DTOs ⇒ no new native reflect-config).
 *
 * <p>Nothing persists; presence is deliberately best-effort UX.
 */
@Component
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String SUBJECT_PREFIX = "kelta.presence.";
    static final int MAX_PRESENCE_PER_SESSION = 10;
    static final int MAX_USERS_PER_SNAPSHOT = 20;
    static final long HEARTBEAT_SECONDS = 30;
    static final long EXPIRY_MILLIS = 90_000;

    /** One remote-or-local user present on a resource. */
    record PresenceEntry(String id, String email, long lastSeenMillis) {}

    // "tenantId:resource" → userId → entry (fleet-wide view, fed by NATS deltas).
    private final Map<String, Map<String, PresenceEntry>> presence = new ConcurrentHashMap<>();
    // "tenantId:resource" → LOCAL sessions joined (receive presence.changed).
    private final Map<String, Set<WebSocketSession>> localSessions = new ConcurrentHashMap<>();
    // sessionId → set of joined "tenantId:resource" keys (cleanup + heartbeat).
    private final Map<String, Set<String>> sessionResources = new ConcurrentHashMap<>();
    // sessionId → session (heartbeat re-announce needs the identity attributes).
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final NatsConnectionManager connectionManager;
    private final ScheduledExecutorService scheduler;

    public PresenceService(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "presence-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleAtFixedRate(this::heartbeatAndSweep,
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Join a session to a resource. Returns false when the per-session cap is hit. */
    public boolean join(WebSocketSession session, String tenantId, String resource) {
        Set<String> joined = sessionResources.computeIfAbsent(
                session.getId(), k -> ConcurrentHashMap.newKeySet());
        String key = tenantId + ":" + resource;
        if (!joined.contains(key) && joined.size() >= MAX_PRESENCE_PER_SESSION) {
            return false;
        }
        joined.add(key);
        sessions.put(session.getId(), session);
        localSessions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        publishDelta(tenantId, resource, session, "join");
        // The local pod applies its own delta via the NATS broadcast loopback; also
        // apply eagerly so the joiner's first snapshot includes itself without
        // waiting a round-trip.
        applyDelta(key, userOf(session), "join");
        broadcastSnapshot(tenantId, resource);
        return true;
    }

    /** Leave one resource. */
    public void leave(WebSocketSession session, String tenantId, String resource) {
        String key = tenantId + ":" + resource;
        Set<String> joined = sessionResources.get(session.getId());
        if (joined == null || !joined.remove(key)) return;
        Set<WebSocketSession> locals = localSessions.get(key);
        if (locals != null) locals.remove(session);
        publishDelta(tenantId, resource, session, "leave");
        applyDelta(key, userOf(session), "leave");
        broadcastSnapshot(tenantId, resource);
    }

    /** Leave everything on disconnect. */
    public void removeSession(WebSocketSession session) {
        Set<String> joined = sessionResources.remove(session.getId());
        sessions.remove(session.getId());
        if (joined == null) return;
        String tenantId = (String) session.getAttributes().get("tenantId");
        for (String key : joined) {
            Set<WebSocketSession> locals = localSessions.get(key);
            if (locals != null) locals.remove(session);
            String resource = key.substring((tenantId + ":").length());
            publishDelta(tenantId, resource, session, "leave");
            applyDelta(key, userOf(session), "leave");
            broadcastSnapshot(tenantId, resource);
        }
    }

    /** NATS consumer: apply a remote (or loopback) delta and rebroadcast locally. */
    public void onPresenceEvent(String message) {
        try {
            JsonNode root = MAPPER.readTree(message);
            String tenantId = text(root, "tenantId");
            String resource = text(root, "resource");
            String type = text(root, "type");
            JsonNode user = root.get("user");
            if (tenantId == null || resource == null || type == null || user == null) return;
            String userId = text(user, "id");
            if (userId == null) return;

            String key = tenantId + ":" + resource;
            boolean changed = applyDelta(key,
                    Map.of("id", userId, "email", text(user, "email") == null ? "" : text(user, "email")),
                    type);
            if (changed) {
                broadcastSnapshot(tenantId, resource);
            }
        } catch (Exception e) {
            log.warn("Failed to process presence event: {}", e.getMessage());
        }
    }

    /** Re-announce local sessions and expire silent entries. */
    void heartbeatAndSweep() {
        try {
            // 1) Heartbeat every local session's joined resources.
            for (Map.Entry<String, Set<String>> entry : sessionResources.entrySet()) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session == null || !session.isOpen()) continue;
                String tenantId = (String) session.getAttributes().get("tenantId");
                for (String key : entry.getValue()) {
                    String resource = key.substring((tenantId + ":").length());
                    publishDelta(tenantId, resource, session, "heartbeat");
                    applyDelta(key, userOf(session), "heartbeat");
                }
            }
            // 2) Expire entries not seen within the window.
            long cutoff = System.currentTimeMillis() - EXPIRY_MILLIS;
            for (Map.Entry<String, Map<String, PresenceEntry>> entry : presence.entrySet()) {
                boolean removed = entry.getValue().values()
                        .removeIf(user -> user.lastSeenMillis() < cutoff);
                if (removed) {
                    int idx = entry.getKey().indexOf(':');
                    broadcastSnapshot(entry.getKey().substring(0, idx),
                            entry.getKey().substring(idx + 1));
                }
            }
        } catch (Exception e) {
            log.warn("Presence heartbeat/sweep failed: {}", e.getMessage());
        }
    }

    /** Apply a delta to the fleet-wide view. Returns true when the user set changed. */
    private boolean applyDelta(String key, Map<String, String> user, String type) {
        Map<String, PresenceEntry> users =
                presence.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        String id = user.get("id");
        if ("leave".equals(type)) {
            return users.remove(id) != null;
        }
        PresenceEntry previous = users.put(id,
                new PresenceEntry(id, user.getOrDefault("email", ""), System.currentTimeMillis()));
        // join of a new user changes the set; heartbeat/rejoin of a known one does not.
        return previous == null;
    }

    /** Push the current snapshot to every LOCAL session joined to the resource. */
    private void broadcastSnapshot(String tenantId, String resource) {
        String key = tenantId + ":" + resource;
        Set<WebSocketSession> locals = localSessions.get(key);
        if (locals == null || locals.isEmpty()) return;

        Map<String, PresenceEntry> users = presence.getOrDefault(key, Map.of());
        List<Map<String, String>> snapshot = users.values().stream()
                .limit(MAX_USERS_PER_SNAPSHOT)
                .map(u -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("id", u.id());
                    if (u.email() != null && !u.email().isBlank()) m.put("email", u.email());
                    return m;
                })
                .toList();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "presence.changed");
        event.put("resource", resource);
        event.put("users", snapshot);
        event.put("timestamp", Instant.now().toString());

        for (WebSocketSession session : locals) {
            if (session.isOpen()) sendToSession(session, event);
        }
    }

    private void publishDelta(String tenantId, String resource, WebSocketSession session, String type) {
        try {
            Map<String, String> user = userOf(session);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("type", type);
            payload.put("resource", resource);
            payload.put("user", user);
            connectionManager.jetStream().publishAsync(
                    SUBJECT_PREFIX + tenantId, MAPPER.writeValueAsBytes(payload));
        } catch (Exception e) {
            log.warn("Failed to publish presence {} for {}: {}", type, resource, e.getMessage());
        }
    }

    private static Map<String, String> userOf(WebSocketSession session) {
        String userId = String.valueOf(session.getAttributes().getOrDefault("userId", ""));
        Object email = session.getAttributes().get("userEmail");
        Map<String, String> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("email", email != null ? email.toString() : "");
        return user;
    }

    /** Mirrors RealtimeWebSocketHandler.sendMessage (outbound sink on the session). */
    @SuppressWarnings("unchecked")
    private void sendToSession(WebSocketSession session, Map<String, ?> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            Sinks.Many<WebSocketMessage> outbound =
                    (Sinks.Many<WebSocketMessage>) session.getAttributes().get("outbound");
            if (outbound != null) {
                outbound.tryEmitNext(session.textMessage(json));
            }
        } catch (Exception e) {
            log.warn("Failed to send presence snapshot: {}", e.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.stringValue().isBlank()
                ? value.stringValue() : null;
    }
}
