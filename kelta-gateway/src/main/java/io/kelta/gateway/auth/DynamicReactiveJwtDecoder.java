package io.kelta.gateway.auth;

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
 * Only accepts issuers registered in the worker's OIDC provider database.
 *
 * <p>Validation flow:
 * <ol>
 *   <li>Parse the JWT to extract the issuer (without signature validation)</li>
 *   <li>Look up the OIDC provider configuration from the worker service (cached in Redis)</li>
 *   <li>Reject the token if the issuer is not registered</li>
 *   <li>Create/cache a NimbusReactiveJwtDecoder per JWKS URI</li>
 *   <li>Validate the token with the correct decoder</li>
 * </ol>
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
        return decode(token, null);
    }

    /**
     * Decodes a JWT token with tenant-scoped OIDC provider validation.
     * When tenantId is provided, the OIDC provider lookup is scoped to that tenant,
     * preventing cross-tenant JWT acceptance.
     *
     * @param token the JWT token string
     * @param tenantId the tenant ID for scoped provider lookup (null for unscoped — not recommended)
     * @return a Mono emitting the decoded JWT
     * @throws JwtException if the token is invalid or the issuer is not registered for the tenant
     */
    public Mono<Jwt> decode(String token, String tenantId) throws JwtException {
        String issuer;
        try {
            issuer = extractIssuer(token);
        } catch (Exception e) {
            return Mono.error(new JwtException("Failed to parse JWT issuer: " + e.getMessage()));
        }

        if (issuer == null) {
            return Mono.error(new JwtException("JWT missing issuer (iss) claim"));
        }

        return resolveProviderInfo(issuer, tenantId)
                .flatMap(info -> decodeWithProviderInfo(token, info));
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
     * Resolves the provider info (JWKS URI + audience) for an issuer, scoped to a tenant.
     * Checks Redis cache first for JWKS URI, then calls the worker's internal API.
     *
     * @param issuer the OIDC issuer URI from the JWT
     * @param tenantId the tenant ID for scoped lookup (null for unscoped — not recommended)
     */
    private Mono<ProviderInfo> resolveProviderInfo(String issuer, String tenantId) {
        // Cache key includes tenant ID to prevent cross-tenant cache poisoning
        String cacheKey = tenantId != null ? tenantId + ":" + issuer : issuer;

        if (redisTemplate == null) {
            return lookupFromWorker(issuer, tenantId);
        }

        String redisKey = REDIS_PREFIX + cacheKey;
        return redisTemplate.opsForValue().get(redisKey)
                .map(jwksUri -> new ProviderInfo(jwksUri, audienceCache.get(cacheKey)))
                .switchIfEmpty(
                        lookupFromWorker(issuer, tenantId)
                                .flatMap(info ->
                                        redisTemplate.opsForValue()
                                                .set(redisKey, info.jwksUri(), PROVIDER_CACHE_TTL)
                                                .thenReturn(info)));
    }

    private Mono<ProviderInfo> lookupFromWorker(String issuer, String tenantId) {
        log.debug("Looking up OIDC provider from worker for issuer={} tenant={}", issuer, tenantId);

        String uri = tenantId != null
                ? "/internal/oidc/by-issuer?issuer={issuer}&tenantId={tenantId}"
                : "/internal/oidc/by-issuer?issuer={issuer}";

        // Cache key includes tenant ID to prevent cross-tenant cache poisoning
        String cacheKey = tenantId != null ? tenantId + ":" + issuer : issuer;

        WebClient.RequestHeadersSpec<?> request = tenantId != null
                ? workerClient.get().uri(uri, issuer, tenantId)
                : workerClient.get().uri(uri, issuer);

        return request
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String jwksUri = json.get("jwksUri").asText();
                    String audience = null;
                    JsonNode audienceNode = json.get("audience");
                    if (audienceNode != null && !audienceNode.isNull() && !audienceNode.asText().isBlank()) {
                        audience = audienceNode.asText();
                        audienceCache.put(cacheKey, audience);
                        log.debug("Worker returned audience: {} for issuer: {}", audience, issuer);
                    }
                    log.debug("Worker returned JWKS URI: {} for issuer={} tenant={}", jwksUri, issuer, tenantId);
                    return new ProviderInfo(jwksUri, audience);
                });
        // No fallback — if the issuer is not registered in the worker's OIDC provider
        // database for this tenant, the token is rejected. This prevents cross-tenant
        // JWT acceptance and attackers from using arbitrary issuers.
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
