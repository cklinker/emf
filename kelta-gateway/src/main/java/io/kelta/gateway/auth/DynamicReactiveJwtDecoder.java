package io.kelta.gateway.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Reactive JWT decoder for the API gateway. Accepts only tokens issued by the
 * internal kelta-auth provider — external IdPs federate through kelta-auth,
 * which mints platform-issued JWTs.
 *
 * <p>Validation flow:
 * <ol>
 *   <li>Parse the JWT to extract the {@code iss} claim (without signature validation).</li>
 *   <li>Reject if {@code iss} does not match the configured kelta-auth issuer.</li>
 *   <li>Validate signature and timestamps against kelta-auth's JWKS.</li>
 * </ol>
 */
public class DynamicReactiveJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(DynamicReactiveJwtDecoder.class);

    private final String issuerUri;
    private final Duration clockSkew;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile NimbusReactiveJwtDecoder delegate;

    public DynamicReactiveJwtDecoder(String issuerUri) {
        this(issuerUri, Duration.ZERO);
    }

    public DynamicReactiveJwtDecoder(String issuerUri, Duration clockSkew) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("issuerUri must be set to the internal kelta-auth issuer");
        }
        this.issuerUri = issuerUri;
        this.clockSkew = clockSkew != null ? clockSkew : Duration.ZERO;
        log.info("DynamicReactiveJwtDecoder initialized: issuerUri={}, clockSkew={}s",
                issuerUri, this.clockSkew.getSeconds());
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        return decode(token, null);
    }

    /**
     * Decodes a JWT. The {@code tenantId} parameter is retained for source
     * compatibility with callers and ignored — issuer validation is global
     * because only kelta-auth-issued tokens are accepted.
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

        if (!issuerUri.equals(issuer)) {
            log.warn("Rejecting JWT: iss={} expected={}", issuer, issuerUri);
            return Mono.error(new JwtException(
                    "JWT issuer not accepted: only tokens issued by " + issuerUri + " are allowed"));
        }

        return delegate().decode(token);
    }

    private NimbusReactiveJwtDecoder delegate() {
        NimbusReactiveJwtDecoder local = delegate;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (delegate == null) {
                String jwksUri = issuerUri.endsWith("/") ? issuerUri + "oauth2/jwks" : issuerUri + "/oauth2/jwks";
                NimbusReactiveJwtDecoder nimbus = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
                nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        List.of(new JwtTimestampValidator(clockSkew))));
                log.debug("Initialized JWKS decoder for {}", jwksUri);
                delegate = nimbus;
            }
            return delegate;
        }
    }

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
     * Drops the cached JWKS decoder so the next decode rebuilds it. Called when
     * key rotation or configuration changes are signaled.
     */
    public void evictAll() {
        synchronized (this) {
            delegate = null;
        }
        log.info("Evicted cached JWT decoder");
    }
}
