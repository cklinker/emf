package io.kelta.gateway.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe registry for WebSocket subscriptions.
 *
 * <p>Tracks which sessions are subscribed to which collections,
 * with tenant isolation and per-session/per-tenant limits.
 *
 * @since 1.0.0
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    static final int MAX_SUBSCRIPTIONS_PER_SESSION = 50;
    static final int MAX_CONNECTIONS_PER_TENANT = 100;

    // sessionId → set of "tenantId:collectionName" keys
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    // "tenantId:collectionName" → set of sessions
    private final Map<String, Set<WebSocketSession>> routingIndex = new ConcurrentHashMap<>();

    // tenantId → connection count
    private final Map<String, AtomicInteger> tenantConnectionCount = new ConcurrentHashMap<>();

    // sessionId → tenantId (for cleanup)
    private final Map<String, String> sessionTenants = new ConcurrentHashMap<>();

    /**
     * Register a new connection. Returns false if tenant limit exceeded.
     */
    public boolean registerConnection(WebSocketSession session, String tenantId) {
        AtomicInteger count = tenantConnectionCount.computeIfAbsent(tenantId, k -> new AtomicInteger(0));
        if (count.get() >= MAX_CONNECTIONS_PER_TENANT) {
            log.warn("WebSocket connection limit reached for tenant {}: {}", tenantId, count.get());
            return false;
        }
        count.incrementAndGet();
        sessionTenants.put(session.getId(), tenantId);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        return true;
    }

    /**
     * Subscribe a session to a collection. Returns false if subscription limit reached.
     */
    public boolean subscribe(WebSocketSession session, String tenantId, String collectionName) {
        Set<String> subs = sessionSubscriptions.get(session.getId());
        if (subs == null) return false;

        if (subs.size() >= MAX_SUBSCRIPTIONS_PER_SESSION) {
            log.warn("Subscription limit reached for session {}: {}", session.getId(), subs.size());
            return false;
        }

        String key = tenantId + ":" + collectionName;
        subs.add(key);
        routingIndex.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(session);

        log.debug("Session {} subscribed to {} (total: {})", session.getId(), key, subs.size());
        return true;
    }

    /**
     * Unsubscribe a session from a collection.
     */
    public void unsubscribe(WebSocketSession session, String tenantId, String collectionName) {
        String key = tenantId + ":" + collectionName;

        Set<String> subs = sessionSubscriptions.get(session.getId());
        if (subs != null) {
            subs.remove(key);
        }

        Set<WebSocketSession> sessions = routingIndex.get(key);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                routingIndex.remove(key);
            }
        }
    }

    /**
     * Remove all subscriptions for a session (on disconnect).
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        Set<String> subs = sessionSubscriptions.remove(sessionId);

        if (subs != null) {
            for (String key : subs) {
                Set<WebSocketSession> sessions = routingIndex.get(key);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        routingIndex.remove(key);
                    }
                }
            }
        }

        String tenantId = sessionTenants.remove(sessionId);
        if (tenantId != null) {
            AtomicInteger count = tenantConnectionCount.get(tenantId);
            if (count != null) {
                int remaining = count.decrementAndGet();
                if (remaining <= 0) {
                    tenantConnectionCount.remove(tenantId);
                }
            }
        }

        log.debug("Session {} removed, cleaned {} subscriptions", sessionId, subs != null ? subs.size() : 0);
    }

    /**
     * Get all sessions subscribed to a tenant+collection.
     */
    public Set<WebSocketSession> getSubscribers(String tenantId, String collectionName) {
        String key = tenantId + ":" + collectionName;
        Set<WebSocketSession> sessions = routingIndex.get(key);
        return sessions != null ? Set.copyOf(sessions) : Set.of();
    }

    /**
     * Get the count of active connections for a tenant.
     */
    public int getConnectionCount(String tenantId) {
        AtomicInteger count = tenantConnectionCount.get(tenantId);
        return count != null ? count.get() : 0;
    }

    /**
     * Get the count of subscriptions for a session.
     */
    public int getSubscriptionCount(String sessionId) {
        Set<String> subs = sessionSubscriptions.get(sessionId);
        return subs != null ? subs.size() : 0;
    }
}
