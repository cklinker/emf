package com.emf.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DynamicReactiveJwtDecoder")
class DynamicReactiveJwtDecoderTest {

    private DynamicReactiveJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        // No Redis, no control plane — will fall back to default issuer
        decoder = new DynamicReactiveJwtDecoder(
                null, null, "http://localhost:9000/realms/emf");
    }

    @Test
    @DisplayName("should reject invalid JWT format")
    void rejectInvalidJwt() {
        StepVerifier.create(decoder.decode("not.a.jwt"))
                .expectError(JwtException.class)
                .verify();
    }

    @Test
    @DisplayName("should reject token without issuer")
    void rejectTokenWithoutIssuer() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user\"}".getBytes());
        String token = header + "." + payload + ".signature";

        StepVerifier.create(decoder.decode(token))
                .expectError(JwtException.class)
                .verify();
    }

    @Test
    @DisplayName("evictAll should clear decoder cache")
    void evictAllClearsCache() {
        decoder.evictAll();
        // no exception — cache is empty
    }

    @Nested
    @DisplayName("Clock Skew Configuration")
    class ClockSkewTest {

        @Test
        @DisplayName("should accept zero clock skew via 3-arg constructor")
        void shouldAcceptZeroClockSkewDefault() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    null, null, "http://localhost:9000/realms/emf");
            // No exception — default clock skew is Duration.ZERO
            d.evictAll();
        }

        @Test
        @DisplayName("should accept explicit clock skew via 4-arg constructor")
        void shouldAcceptExplicitClockSkew() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    null, null, "http://localhost:9000/realms/emf",
                    Duration.ofSeconds(30));
            // No exception — clock skew is 30 seconds
            d.evictAll();
        }

        @Test
        @DisplayName("should handle null clock skew gracefully")
        void shouldHandleNullClockSkew() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    null, null, "http://localhost:9000/realms/emf", null);
            // No exception — null clock skew defaults to Duration.ZERO
            d.evictAll();
        }

        @Test
        @DisplayName("should reject invalid JWT even with clock skew configured")
        void shouldStillRejectInvalidJwtWithClockSkew() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    null, null, "http://localhost:9000/realms/emf",
                    Duration.ofSeconds(60));

            StepVerifier.create(d.decode("not.a.jwt"))
                    .expectError(JwtException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("AudienceValidator")
    class AudienceValidatorTest {

        @Test
        @DisplayName("should succeed when JWT audience contains expected value")
        void shouldSucceedWhenAudienceMatches() {
            DynamicReactiveJwtDecoder.AudienceValidator validator =
                    new DynamicReactiveJwtDecoder.AudienceValidator("my-client");

            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("aud", List.of("my-client", "other-client"))
                    .claim("sub", "user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            OAuth2TokenValidatorResult result = validator.validate(jwt);
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("should fail when JWT audience does not contain expected value")
        void shouldFailWhenAudienceDoesNotMatch() {
            DynamicReactiveJwtDecoder.AudienceValidator validator =
                    new DynamicReactiveJwtDecoder.AudienceValidator("my-client");

            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("aud", List.of("other-client"))
                    .claim("sub", "user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            OAuth2TokenValidatorResult result = validator.validate(jwt);
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("should fail when JWT has no audience claim")
        void shouldFailWhenNoAudience() {
            DynamicReactiveJwtDecoder.AudienceValidator validator =
                    new DynamicReactiveJwtDecoder.AudienceValidator("my-client");

            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("sub", "user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            OAuth2TokenValidatorResult result = validator.validate(jwt);
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("should return expected audience value")
        void shouldReturnExpectedAudience() {
            DynamicReactiveJwtDecoder.AudienceValidator validator =
                    new DynamicReactiveJwtDecoder.AudienceValidator("my-client");

            assertThat(validator.getExpectedAudience()).isEqualTo("my-client");
        }
    }
}
