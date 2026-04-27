package io.kelta.mcp.auth;

import io.kelta.mcp.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of MCP session id -> Personal Access Token.
 *
 * <p>The PAT is captured when an MCP session is initialized (the HTTP request
 * that opens the session carries {@code Authorization: Bearer klt_*}) and is
 * then needed on every tool call to forward to the gateway. SSE frames carrying
 * subsequent tool calls do not necessarily re-include the header, so we cache
 * by session id.
 *
 * <p>Tokens are never persisted, never logged. Sessions evict on close,
 * idle timeout, or pod shutdown.
 */
@Component
public class PatSessionStore {

    private static final Logger log = LoggerFactory.getLogger(PatSessionStore.class);

    private record Entry(String pat, Instant lastSeen) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final Duration idleTtl;

    public PatSessionStore(McpProperties properties) {
        this.idleTtl = Duration.ofMinutes(properties.sessionTtlMinutes());
    }

    public void put(String sessionId, String pat) {
        sessions.put(sessionId, new Entry(pat, Instant.now()));
    }

    public String touchAndGet(String sessionId) {
        Entry e = sessions.computeIfPresent(sessionId,
                (k, v) -> new Entry(v.pat(), Instant.now()));
        return e == null ? null : e.pat();
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    @Scheduled(fixedDelayString = "PT1M")
    void evictIdle() {
        Instant cutoff = Instant.now().minus(idleTtl);
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().lastSeen().isBefore(cutoff));
        int evicted = before - sessions.size();
        if (evicted > 0) {
            log.info("Evicted {} idle MCP sessions", evicted);
        }
    }
}
