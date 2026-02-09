package com.emf.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic JWT decoder that resolves the OIDC provider from the token's issuer claim.
 * Supports multi-provider JWT validation by:
 * 1. Parsing the JWT to extract the issuer (without signature validation)
 * 2. Looking up the OIDC provider configuration from the control plane (cached in Redis)
 * 3. Creating/caching a NimbusReactiveJwtDecoder per JWKS URI
 * 4. Validating the token with the correct decoder
 */
public class DynamicReactiveJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(DynamicReactiveJwtDecoder.class);
    private static final Duration PROVIDER_CACHE_TTL = Duration.ofMinutes(15);
    private static final String REDIS_PREFIX = "oidc:provider:";

    private final WebClient controlPlaneClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String fallbackIssuerUri;

    // In-memory cache of JwtDecoders keyed by JWKS URI
    private final ConcurrentHashMap<String, NimbusReactiveJwtDecoder> decoderCache = new ConcurrentHashMap<>();

    public DynamicReactiveJwtDecoder(
            WebClient controlPlaneClient,
            @Nullable ReactiveStringRedisTemplate redisTemplate,
            String fallbackIssuerUri) {
        this.controlPlaneClient = controlPlaneClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.fallbackIssuerUri = fallbackIssuerUri;
        log.info("DynamicReactiveJwtDecoder initialized: fallbackIssuerUri={}, redis={}",
                fallbackIssuerUri, redisTemplate != null ? "enabled" : "disabled");
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        String issuer;
        try {
            issuer = extractIssuer(token);
        } catch (Exception e) {
            return Mono.error(new JwtException("Failed to parse JWT issuer: " + e.getMessage()));
        }

        if (issuer == null) {
            return Mono.error(new JwtException("JWT missing issuer (iss) claim"));
        }

        return resolveJwksUri(issuer)
                .flatMap(jwksUri -> decodeWithJwksUri(token, jwksUri))
                .onErrorResume(e -> {
                    // Primary JWKS URI failed (stale DB, unreachable endpoint, etc.)
                    // Fall back to standard OIDC discovery from the issuer URL
                    log.warn("JWT decode failed for issuer {}, trying OIDC discovery: {}",
                            issuer, e.getMessage());
                    return discoverJwksUri(issuer)
                            .flatMap(jwksUri -> {
                                // Update Redis cache with the discovered URI
                                Mono<Boolean> cacheUpdate = redisTemplate != null
                                        ? redisTemplate.opsForValue()
                                                .set(REDIS_PREFIX + issuer, jwksUri, PROVIDER_CACHE_TTL)
                                        : Mono.just(true);
                                return cacheUpdate.then(decodeWithJwksUri(token, jwksUri));
                            });
                });
    }

    private Mono<Jwt> decodeWithJwksUri(String token, String jwksUri) {
        NimbusReactiveJwtDecoder decoder = decoderCache.computeIfAbsent(
                jwksUri,
                uri -> NimbusReactiveJwtDecoder.withJwkSetUri(uri).build());
        return decoder.decode(token);
    }

    /**
     * Extracts the issuer from the JWT payload without signature validation.
     */
    private String extractIssuer(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode issNode = node.get("iss");
            return issNode != null ? issNode.asText() : null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JWT payload", e);
        }
    }

    /**
     * Resolves the JWKS URI for an issuer by looking up the OIDC provider.
     * Checks Redis cache first, then calls the control plane's internal API.
     * Falls back to the configured default OIDC issuer if no provider is found.
     */
    private Mono<String> resolveJwksUri(String issuer) {
        if (redisTemplate == null) {
            return lookupFromControlPlane(issuer);
        }

        String redisKey = REDIS_PREFIX + issuer;
        return redisTemplate.opsForValue().get(redisKey)
                .switchIfEmpty(
                        lookupFromControlPlane(issuer)
                                .flatMap(jwksUri ->
                                        redisTemplate.opsForValue()
                                                .set(redisKey, jwksUri, PROVIDER_CACHE_TTL)
                                                .thenReturn(jwksUri)));
    }

    private Mono<String> lookupFromControlPlane(String issuer) {
        log.debug("Looking up OIDC provider from control plane for issuer: {}", issuer);
        return controlPlaneClient.get()
                .uri("/internal/oidc/by-issuer?issuer={issuer}", issuer)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String jwksUri = json.get("jwksUri").asText();
                    log.debug("Control plane returned JWKS URI: {} for issuer: {}", jwksUri, issuer);
                    return jwksUri;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to lookup OIDC provider for issuer {}, using OIDC discovery: {}",
                            issuer, e.getMessage());
                    return discoverJwksUri(issuer);
                });
    }

    /**
     * Discovers the JWKS URI from the issuer's standard OIDC discovery endpoint.
     * This is a fallback when the control plane lookup fails or returns stale data.
     */
    private Mono<String> discoverJwksUri(String issuer) {
        String discoveryUrl = issuer.replaceAll("/$", "") + "/.well-known/openid-configuration";
        return WebClient.create().get()
                .uri(discoveryUrl)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("jwks_uri").asText())
                .onErrorResume(e -> {
                    log.error("OIDC discovery failed for issuer {}: {}", issuer, e.getMessage());
                    return Mono.just(fallbackIssuerUri + "/protocol/openid-connect/certs");
                });
    }

    /**
     * Evicts all cached decoders (called when OIDC configuration changes).
     */
    public void evictAll() {
        decoderCache.clear();
        log.info("Evicted all cached JWT decoders");
    }
}
