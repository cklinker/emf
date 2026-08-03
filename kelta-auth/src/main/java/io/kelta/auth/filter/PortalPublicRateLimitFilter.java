package io.kelta.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-IP rate limiting for kelta-auth's public portal endpoints
 * (consumer-alerting slice 7).
 *
 * <p><b>Why this is not in the gateway.</b> The obvious home for a per-IP budget
 * on {@code /portal/api/signup} is the gateway's {@code IpRateLimitFilter} — and
 * the slice spec put it there. But {@code /portal/**} is served by kelta-auth,
 * which has its own ingress; that traffic never passes through the gateway. A
 * gateway entry for these paths would match nothing while reading, in config and
 * in review, as though signup were rate limited. The control has to live where
 * the request actually lands.
 *
 * <p><b>Fixed window in Redis, TTL set only when the window is new.</b> Same
 * shape as the gateway's limiter, and the TTL rule is the load-bearing part:
 * refreshing the TTL on every request converts the fixed window into an
 * idle-expiry, so under sustained traffic the counter never resets and the caller
 * is locked out permanently — each rejected request extends its own lockout. That
 * regression cost a production incident on 2026-07-11; {@code windowTtlIsNotRefreshed}
 * in the test guards it here. The gateway keeps a parallel copy because it is
 * reactive and this one is servlet-blocking; the two must stay behaviourally
 * identical.
 *
 * <p><b>Fails open</b> if Redis is unreachable, matching the gateway. Losing the
 * cache must not take signup down, and the bot challenge and per-email budget
 * still apply.
 *
 * <p>Off unless {@code kelta.auth.rate-limit.ip-paths} is non-empty; it ships with
 * budgets for the three public portal paths.
 */
@Component
@Order(-150)
public class PortalPublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PortalPublicRateLimitFilter.class);

    static final int DEFAULT_REQUESTS_PER_WINDOW = 60;
    static final Duration WINDOW = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "authratelimit:ip:";

    /**
     * A human signs up once and a magic-link request that succeeded needs no
     * retry, so these are tight. The challenge endpoint is looser because a
     * legitimate client fetches one per form render (and may retry on reload).
     *
     * <p>{@code /portal/login/request} — the Thymeleaf form — is listed alongside
     * the JSON API because it sends the <em>same</em> email through the same
     * service. Limiting only the headless path would leave the identical abuse
     * open one URL over. Its prefix necessarily also covers {@code /portal/login}
     * and {@code /portal/login/verify}; that is fine — clicking a link you were
     * emailed is well inside a budget of 10 a minute.
     */
    static final String DEFAULT_IP_PATHS =
            "/portal/api/signup=5,/portal/api/login/request=10,/portal/api/challenge=30,"
            + "/portal/login=10";

    /** See {@code RedisRateLimiter} in kelta-gateway — same semantics. */
    private static final RedisScript<Long> INCREMENT_WINDOW_SCRIPT = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) < 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Integer> pathBudgets;
    private final int trustedProxyCount;

    public PortalPublicRateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${kelta.auth.rate-limit.ip-paths:" + DEFAULT_IP_PATHS + "}") List<String> ipPaths,
            @Value("${kelta.auth.rate-limit.trusted-proxy-count:1}") int trustedProxyCount) {
        this.redisTemplate = redisTemplate;
        this.pathBudgets = parsePathBudgets(ipPaths);
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
        log.info("PortalPublicRateLimitFilter initialized: {} path(s) {} over a {}s window, "
                        + "{} trusted proxy hop(s)",
                pathBudgets.size(), pathBudgets, WINDOW.toSeconds(), this.trustedProxyCount);
    }

    /** {@code <prefix>=<limit>} entries, longest prefix first; bad entries skipped. */
    static Map<String, Integer> parsePathBudgets(List<String> entries) {
        if (entries == null) {
            return Map.of();
        }
        Map<String, Integer> parsed = new LinkedHashMap<>();
        entries.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .sorted((a, b) -> Integer.compare(pathOf(b).length(), pathOf(a).length()))
                .forEach(entry -> {
                    String path = pathOf(entry);
                    if (path.isEmpty()) {
                        log.warn("Ignoring rate-limit path entry with empty path: '{}'", entry);
                        return;
                    }
                    int limit = DEFAULT_REQUESTS_PER_WINDOW;
                    int eq = entry.indexOf('=');
                    if (eq >= 0) {
                        try {
                            limit = Integer.parseInt(entry.substring(eq + 1).trim());
                        } catch (NumberFormatException e) {
                            log.warn("Invalid limit in rate-limit path entry '{}'; using default {}",
                                    entry, DEFAULT_REQUESTS_PER_WINDOW);
                        }
                    }
                    if (limit <= 0) {
                        log.warn("Ignoring non-positive limit in rate-limit path entry '{}'", entry);
                        return;
                    }
                    parsed.put(path, limit);
                });
        return Collections.unmodifiableMap(parsed);
    }

    private static String pathOf(String entry) {
        int eq = entry.indexOf('=');
        return (eq >= 0 ? entry.substring(0, eq) : entry).trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String matched = matchPath(request.getRequestURI());
        if (matched == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        int limit = pathBudgets.getOrDefault(matched, DEFAULT_REQUESTS_PER_WINDOW);
        String key = KEY_PREFIX + matched + ":" + clientIp;
        long count = increment(key);

        if (count > limit) {
            log.warn("Portal rate limit exceeded for {} on {} (bucket {}): {} > {} per {}s",
                    clientIp, request.getRequestURI(), matched, count, limit, WINDOW.toSeconds());
            tooManyRequests(request, response, retryAfterSeconds(key));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The window's real remaining TTL. Reporting the full window instead would
     * tell a well-behaved client to back off far longer than necessary — and a
     * misbehaving one ignores the header anyway.
     */
    private long retryAfterSeconds(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key);
            return ttl != null && ttl > 0 ? ttl : WINDOW.toSeconds();
        } catch (RuntimeException e) {
            return WINDOW.toSeconds();
        }
    }

    /** Longest configured prefix matching {@code path}, or null if unlimited. */
    String matchPath(String path) {
        if (path == null) {
            return null;
        }
        for (String prefix : pathBudgets.keySet()) {
            if (path.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * Counts the request. Returns 0 (i.e. always under the limit) when Redis is
     * unavailable — see the fail-open note on the class.
     */
    private long increment(String key) {
        try {
            Long count = redisTemplate.execute(INCREMENT_WINDOW_SCRIPT, List.of(key),
                    String.valueOf(WINDOW.toSeconds()));
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            log.warn("Redis unavailable for portal rate limiting — allowing request: {}",
                    e.getMessage());
            return 0;
        }
    }

    /**
     * Resolves the client address for the limiter key.
     *
     * <p>Takes the hop the <b>trusted</b> proxy appended, counting from the right,
     * rather than the leftmost {@code X-Forwarded-For} entry. The leftmost entry
     * is whatever the client sent, so honouring it would let anyone mint a fresh
     * bucket per request by varying one header — the limiter would be decorative.
     * With the default of one trusted proxy that means the last entry.
     */
    String resolveClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank() && trustedProxyCount > 0) {
            String[] hops = header.split(",");
            int index = hops.length - trustedProxyCount;
            if (index < 0) {
                // Fewer hops than expected: the leftmost is the most trustworthy
                // one available, but it may be client-supplied — never index
                // past the start into a spoofable position.
                index = 0;
            }
            String hop = hops[index].trim();
            if (!hop.isEmpty()) {
                return hop;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private void tooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                 long retryAfterSeconds) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.getOutputStream().write(String.format(
                "{\"error\":{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\","
                        + "\"message\":\"Rate limit exceeded. Try again later.\",\"path\":\"%s\"}}",
                request.getRequestURI()).getBytes(StandardCharsets.UTF_8));
    }
}
