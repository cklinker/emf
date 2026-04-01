package io.kelta.auth.service;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches OIDC Discovery metadata from a provider's
 * {@code .well-known/openid-configuration} endpoint.
 *
 * <p>When overrides are present in the OIDC provider configuration,
 * they take precedence over discovered values.
 */
@Service
public class OidcDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(OidcDiscoveryService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final Map<String, CachedDiscovery> cache = new ConcurrentHashMap<>();

    public OidcDiscoveryService() {
        this.restClient = RestClient.builder()
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Resolved OIDC Discovery metadata.
     */
    public record DiscoveryMetadata(
            String authorizationEndpoint,
            String tokenEndpoint,
            String userinfoEndpoint,
            String jwksUri,
            String endSessionEndpoint,
            String issuer
    ) {}

    /**
     * Discovers OIDC endpoints from the provider's issuer URI.
     *
     * @param issuerUri the OIDC issuer URI (e.g., {@code https://accounts.google.com})
     * @return discovered metadata, or empty if Discovery fails
     */
    public Optional<DiscoveryMetadata> discover(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            return Optional.empty();
        }

        // Check cache
        CachedDiscovery cached = cache.get(issuerUri);
        if (cached != null && !cached.isExpired()) {
            return cached.metadata;
        }

        try {
            String discoveryUrl = buildDiscoveryUrl(issuerUri);
            log.debug("Fetching OIDC Discovery from: {}", discoveryUrl);

            JsonNode response = restClient.get()
                    .uri(discoveryUrl)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("Empty Discovery response from: {}", discoveryUrl);
                cacheResult(issuerUri, Optional.empty());
                return Optional.empty();
            }

            DiscoveryMetadata metadata = new DiscoveryMetadata(
                    textOrNull(response, "authorization_endpoint"),
                    textOrNull(response, "token_endpoint"),
                    textOrNull(response, "userinfo_endpoint"),
                    textOrNull(response, "jwks_uri"),
                    textOrNull(response, "end_session_endpoint"),
                    textOrNull(response, "issuer")
            );

            log.info("OIDC Discovery succeeded for issuer={}", issuerUri);
            Optional<DiscoveryMetadata> result = Optional.of(metadata);
            cacheResult(issuerUri, result);
            return result;

        } catch (Exception e) {
            log.warn("OIDC Discovery failed for issuer={}: {}", issuerUri, e.getMessage());
            cacheResult(issuerUri, Optional.empty());
            return Optional.empty();
        }
    }

    /**
     * Resolves endpoints for a provider, applying overrides over discovered values.
     * Override values (non-null, non-blank) take precedence over discovered values.
     *
     * @param issuerUri the OIDC issuer URI
     * @param overrides provider configuration with optional endpoint overrides
     * @return resolved metadata with overrides applied
     */
    public ResolvedEndpoints resolve(String issuerUri, EndpointOverrides overrides) {
        Optional<DiscoveryMetadata> discovered = discover(issuerUri);

        String authorizationUri = coalesce(
                overrides.authorizationUri(),
                discovered.map(DiscoveryMetadata::authorizationEndpoint).orElse(null));
        String tokenUri = coalesce(
                overrides.tokenUri(),
                discovered.map(DiscoveryMetadata::tokenEndpoint).orElse(null));
        String userinfoUri = coalesce(
                overrides.userinfoUri(),
                discovered.map(DiscoveryMetadata::userinfoEndpoint).orElse(null));
        String jwksUri = coalesce(
                overrides.jwksUri(),
                discovered.map(DiscoveryMetadata::jwksUri).orElse(null));
        String endSessionUri = coalesce(
                overrides.endSessionUri(),
                discovered.map(DiscoveryMetadata::endSessionEndpoint).orElse(null));

        String status = discovered.isPresent() ? "discovered" : "manual";
        if (overrides.hasAnyOverride()) {
            status = discovered.isPresent() ? "discovered" : "manual";
        }

        return new ResolvedEndpoints(
                authorizationUri, tokenUri, userinfoUri, jwksUri, endSessionUri, status);
    }

    /**
     * Evicts the cached Discovery result for an issuer.
     */
    public void evict(String issuerUri) {
        cache.remove(issuerUri);
    }

    /**
     * Evicts all cached Discovery results.
     */
    public void evictAll() {
        cache.clear();
    }

    /**
     * Endpoint overrides from the OIDC provider configuration.
     */
    public record EndpointOverrides(
            String authorizationUri,
            String tokenUri,
            String userinfoUri,
            String jwksUri,
            String endSessionUri
    ) {
        public boolean hasAnyOverride() {
            return isNotBlank(authorizationUri) || isNotBlank(tokenUri)
                    || isNotBlank(userinfoUri) || isNotBlank(jwksUri)
                    || isNotBlank(endSessionUri);
        }

        private static boolean isNotBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /**
     * Resolved endpoints with Discovery status.
     */
    public record ResolvedEndpoints(
            String authorizationUri,
            String tokenUri,
            String userinfoUri,
            String jwksUri,
            String endSessionUri,
            String discoveryStatus
    ) {}

    private String buildDiscoveryUrl(String issuerUri) {
        String base = issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
        return base + "/.well-known/openid-configuration";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text.isBlank() ? null : text;
    }

    private String coalesce(String override, String discovered) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return discovered;
    }

    private void cacheResult(String issuerUri, Optional<DiscoveryMetadata> metadata) {
        cache.put(issuerUri, new CachedDiscovery(metadata, Instant.now()));
    }

    private record CachedDiscovery(Optional<DiscoveryMetadata> metadata, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_TTL));
        }
    }
}
