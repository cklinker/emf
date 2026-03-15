package io.kelta.auth.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(ExternalTokenValidator.class);

    private final WorkerClient workerClient;
    private final Map<String, DefaultJWTProcessor<SecurityContext>> processorCache = new ConcurrentHashMap<>();

    public ExternalTokenValidator(WorkerClient workerClient) {
        this.workerClient = workerClient;
    }

    public record ValidatedToken(
            String email, String tenantId, String displayName,
            String subject, String issuer
    ) {}

    public Optional<ValidatedToken> validate(String accessToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet unverifiedClaims = signedJWT.getJWTClaimsSet();
            String issuer = unverifiedClaims.getIssuer();

            if (issuer == null) {
                log.warn("Token has no issuer claim");
                return Optional.empty();
            }

            // Look up the OIDC provider from the worker
            Optional<WorkerClient.OidcProviderInfo> providerOpt =
                    workerClient.findOidcProviderByIssuer(issuer, null);

            if (providerOpt.isEmpty()) {
                log.warn("No OIDC provider found for issuer: {}", issuer);
                return Optional.empty();
            }

            WorkerClient.OidcProviderInfo provider = providerOpt.get();

            // Validate the token signature using the provider's JWKS URI
            DefaultJWTProcessor<SecurityContext> processor = getOrCreateProcessor(provider.jwksUri());
            JWTClaimsSet claims = processor.process(signedJWT, null);

            // Extract user info from validated claims
            String email = extractClaim(claims, "email", "preferred_username", "sub");
            String displayName = extractClaim(claims, "name", "given_name");
            String subject = claims.getSubject();

            if (email == null) {
                log.warn("No email found in token claims");
                return Optional.empty();
            }

            return Optional.of(new ValidatedToken(email, null, displayName, subject, issuer));

        } catch (ParseException e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private DefaultJWTProcessor<SecurityContext> getOrCreateProcessor(String jwksUri) {
        return processorCache.computeIfAbsent(jwksUri, uri -> {
            try {
                var jwkSource = JWKSourceBuilder.create(URI.create(uri).toURL()).build();
                var keySelector = new JWSVerificationKeySelector<>(
                        Set.of(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512),
                        jwkSource
                );
                var processor = new DefaultJWTProcessor<SecurityContext>();
                processor.setJWSKeySelector(keySelector);
                // Skip standard claims verification (we handle issuer ourselves via provider lookup)
                processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                        new JWTClaimsSet.Builder().build(), Set.of()
                ));
                return processor;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create JWT processor for " + uri, e);
            }
        });
    }

    private String extractClaim(JWTClaimsSet claims, String... keys) {
        for (String key : keys) {
            try {
                String value = claims.getStringClaim(key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
}
