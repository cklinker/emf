package io.kelta.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a request's Host header to a tenant slug for custom-domain logins.
 *
 * <p>Mirrors the gateway's {@code CustomDomainFilter} resolution path so that
 * auth (login, OIDC callback) works on the same customer-branded host the
 * gateway accepts. Backed by an in-memory TTL cache to avoid hammering the
 * worker on every request.
 *
 * <p>Reserved platform hosts ({@code *.kelta.io}, {@code localhost}) bypass
 * the worker call entirely.
 */
@Service
public class AuthDomainResolver {

    private static final Logger log = LoggerFactory.getLogger(AuthDomainResolver.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String NOT_FOUND = "__not_found__";

    private final WorkerClient workerClient;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AuthDomainResolver(WorkerClient workerClient) {
        this.workerClient = workerClient;
    }

    /**
     * Returns the tenant slug owning {@code host}, or empty if no verified
     * custom domain exists for that host.
     */
    public Optional<String> resolveTenantSlug(String host) {
        if (host == null || host.isBlank()) return Optional.empty();
        String domain = sanitize(host);
        if (domain == null || isReserved(domain)) return Optional.empty();

        CacheEntry hit = cache.get(domain);
        Instant now = Instant.now();
        if (hit != null && hit.expiresAt.isAfter(now)) {
            return NOT_FOUND.equals(hit.slug) ? Optional.empty() : Optional.of(hit.slug);
        }

        Optional<String> resolved = workerClient.resolveCustomDomain(domain);
        cache.put(domain, new CacheEntry(resolved.orElse(NOT_FOUND), now.plus(TTL)));
        if (resolved.isPresent()) {
            log.debug("Custom domain '{}' resolved to tenant slug '{}'", domain, resolved.get());
        }
        return resolved;
    }

    /** Removes a cached entry — call after we know the mapping has changed. */
    public void invalidate(String host) {
        if (host == null) return;
        String domain = sanitize(host);
        if (domain != null) cache.remove(domain);
    }

    static String sanitize(String host) {
        int colon = host.indexOf(':');
        String d = (colon > 0 ? host.substring(0, colon) : host).toLowerCase().trim();
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.isEmpty() || d.contains("..") || d.startsWith(".") || d.startsWith("-")) return null;
        return d;
    }

    static boolean isReserved(String domain) {
        return "localhost".equals(domain) || "kelta.io".equals(domain) || domain.endsWith(".kelta.io");
    }

    private record CacheEntry(String slug, Instant expiresAt) {}
}
