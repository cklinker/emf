package com.emf.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic JWT decoder that resolves the OIDC provider from the token's issuer claim.
 * Supports multi-provider JWT validation by:
 * 1. Parsing the JWT to extract the issuer (without signature validation)
 * 2. Looking up the OIDC provider configuration from the worker service (cached in Redis)
 * 3. Creating/caching a NimbusReactiveJwtDecoder per JWKS URI
 * 4. Validating the token with the correct decoder
 */
public class DynamicReactiveJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(DynamicReactiveJwtDecoder.class);
    private static final Duration PROVIDER_CACHE_TTL = Duration.ofMinutes(15);
    private static final String REDIS_PREFIX = "oidc:provider:";

    private final WebClient workerClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String fallbackIssuerUri;
    private final Duration clockSkew;

    // In-memory cache of JwtDecoders keyed by JWKS URI
    private final ConcurrentHashMap<String, NimbusReactiveJwtDecoder> decoderCache = new ConcurrentHashMap<>();

    // In-memory cache of expected audiences keyed by issuer URI
    private final ConcurrentHashMap<String, String> audienceCache = new ConcurrentHashMap<>();

    /**
     * Holds provider configuration resolved from the worker service.
     */
    record ProviderInfo(String jwksUri, String audience) {}

    /**
     * Creates a DynamicReactiveJwtDecoder with default clock skew (0 seconds).
     */
    public DynamicReactiveJwtDecoder(
            WebClient workerClient,
            @Nullable ReactiveStringRedisTemplate redisTemplate,
            String fallbackIssuerUri) {
        this(workerClient, redisTemplate, fallbackIssuerUri, Duration.ZERO);
    }

    /**
     * Creates a DynamicReactiveJwtDecoder with configurable clock skew tolerance.
     *
     * @param workerClient WebClient for worker service internal API calls
     * @param redisTemplate Redis template for caching (nullable)
     * @param fallbackIssuerUri fallback OIDC issuer URI
     * @param clockSkew tolerance for clock drift between gateway and identity provider
     */
    public DynamicReactiveJwtDecoder(
            WebClient workerClient,
            @Nullable ReactiveStringRedisTemplate redisTemplate,
            String fallbackIssuerUri,
            Duration clockSkew) {
        this.workerClient = workerClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.fallbackIssuerUri = fallbackIssuerUri;
        this.clockSkew = clockSkew != null ? clockSkew : Duration.ZERO;
        log.info("DynamicReactiveJwtDecoder initialized: fallbackIssuerUri={}, redis={}, clockSkew={}s",
                fallbackIssuerUri, redisTemplate != null ? "enabled" : "disabled",
                this.clockSkew.getSeconds());
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

        return resolveProviderInfo(issuer)
                .flatMap(info -> decodeWithProviderInfo(token, info))
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
                                return cacheUpdate.then(decodeWithProviderInfo(token,
                                        new ProviderInfo(jwksUri, audienceCache.get(issuer))));
                            });
                });
    }

    private Mono<Jwt> decodeWithProviderInfo(String token, ProviderInfo info) {
        NimbusReactiveJwtDecoder decoder = decoderCache.computeIfAbsent(
                info.jwksUri(),
                uri -> {
                    NimbusReactiveJwtDecoder d = NimbusReactiveJwtDecoder.withJwkSetUri(uri).build();
                    // Build a composite validator: timestamp (with clock skew) + optional audience
                    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();

                    // Always add timestamp validation with clock skew tolerance
                    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(clockSkew);
                    validators.add(timestampValidator);
                    if (!clockSkew.isZero()) {
                        log.debug("Added clock skew tolerance of {}s for JWKS URI {}",
                                clockSkew.getSeconds(), uri);
                    }

                    // If audience is configured, add audience validation
                    if (info.audience() != null && !info.audience().isBlank()) {
                        log.debug("Adding audience validation for JWKS URI {}: expected audience={}",
                                uri, info.audience());
                        validators.add(new AudienceValidator(info.audience()));
                    }

                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
                    return d;
                });
        return decoder.decode(token);
    }

    /**
     * JWT validator that checks the 'aud' claim contains the expected audience.
     */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAudience;

        AudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            log.debug("JWT audience validation failed: expected={}, actual={}",
                    expectedAudience, audiences);
            return OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token",
                            "JWT audience does not contain expected value: " + expectedAudience,
                            null));
        }

        String getExpectedAudience() {
            return expectedAudience;
        }
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
     * Resolves the provider info (JWKS URI + audience) for an issuer.
     * Checks Redis cache first for JWKS URI, then calls the worker's internal API.
     * Falls back to the configured default OIDC issuer if no provider is found.
     */
    private Mono<ProviderInfo> resolveProviderInfo(String issuer) {
        if (redisTemplate == null) {
            return lookupFromWorker(issuer);
        }

        String redisKey = REDIS_PREFIX + issuer;
        return redisTemplate.opsForValue().get(redisKey)
                .map(jwksUri -> new ProviderInfo(jwksUri, audienceCache.get(issuer)))
                .switchIfEmpty(
                        lookupFromWorker(issuer)
                                .flatMap(info ->
                                        redisTemplate.opsForValue()
                                                .set(redisKey, info.jwksUri(), PROVIDER_CACHE_TTL)
                                                .thenReturn(info)));
    }

    private Mono<ProviderInfo> lookupFromWorker(String issuer) {
        log.debug("Looking up OIDC provider from worker for issuer: {}", issuer);
        return workerClient.get()
                .uri("/internal/oidc/by-issuer?issuer={issuer}", issuer)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String jwksUri = json.get("jwksUri").asText();
                    String audience = null;
                    JsonNode audienceNode = json.get("audience");
                    if (audienceNode != null && !audienceNode.isNull() && !audienceNode.asText().isBlank()) {
                        audience = audienceNode.asText();
                        audienceCache.put(issuer, audience);
                        log.debug("Worker returned audience: {} for issuer: {}", audience, issuer);
                    }
                    log.debug("Worker returned JWKS URI: {} for issuer: {}", jwksUri, issuer);
                    return new ProviderInfo(jwksUri, audience);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to lookup OIDC provider for issuer {}, using OIDC discovery: {}",
                            issuer, e.getMessage());
                    return discoverJwksUri(issuer)
                            .map(jwksUri -> new ProviderInfo(jwksUri, audienceCache.get(issuer)));
                });
    }

    /**
     * Discovers the JWKS URI from the issuer's standard OIDC discovery endpoint.
     * This is a fallback when the worker lookup fails or returns stale data.
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
        audienceCache.clear();
        log.info("Evicted all cached JWT decoders and audience cache");
    }
}
