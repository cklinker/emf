package io.kelta.gateway.auth;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DynamicReactiveJwtDecoder")
class DynamicReactiveJwtDecoderTest {

    private static final String INTERNAL_ISSUER = "https://auth.kelta.io";

    private DynamicReactiveJwtDecoder decoder;
    private GatewayCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = Mockito.mock(GatewayCacheManager.class);
        Mockito.lenient().when(cacheManager.resolveCustomDomain(Mockito.anyString()))
                .thenReturn(Optional.empty());
        decoder = new DynamicReactiveJwtDecoder(INTERNAL_ISSUER, Duration.ZERO, cacheManager);
    }

    @Test
    @DisplayName("rejects invalid JWT format")
    void rejectInvalidJwt() {
        StepVerifier.create(decoder.decode("not.a.jwt"))
                .expectError(JwtException.class)
                .verify();
    }

    @Test
    @DisplayName("rejects token without iss claim")
    void rejectTokenWithoutIssuer() {
        String token = unsignedToken("{\"sub\":\"user\"}");

        StepVerifier.create(decoder.decode(token))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(JwtException.class);
                    assertThat(err.getMessage()).contains("missing issuer");
                })
                .verify();
    }

    @Test
    @DisplayName("rejects token whose iss is not the platform issuer or a verified custom domain")
    void rejectsForeignIssuer() {
        String token = unsignedToken(
                "{\"iss\":\"https://idp.rzware.com/realms/rzWare\",\"sub\":\"u\"}");

        StepVerifier.create(decoder.decode(token))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(JwtException.class);
                    assertThat(err.getMessage()).contains("not the platform issuer");
                })
                .verify();
    }

    @Test
    @DisplayName("accepts issuer from a verified tenant custom domain (parses past validation)")
    void acceptsVerifiedCustomDomainIssuer() {
        Mockito.when(cacheManager.resolveCustomDomain("acme.com")).thenReturn(Optional.of("acme"));
        String token = unsignedToken("{\"iss\":\"https://acme.com\",\"sub\":\"u\"}");

        // The token will fail downstream (no real signature / JWKS unreachable),
        // but the failure must NOT be our issuer-allow check rejecting acme.com.
        StepVerifier.create(decoder.decode(token))
                .expectErrorSatisfies(err -> assertThat(err.getMessage())
                        .doesNotContain("not the platform issuer"))
                .verify();
    }

    @Test
    @DisplayName("constructor rejects null or blank issuer")
    void constructorRejectsBlankIssuer() {
        assertThatThrownBy(() -> new DynamicReactiveJwtDecoder(null, Duration.ZERO, cacheManager))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DynamicReactiveJwtDecoder("", Duration.ZERO, cacheManager))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("evictAll clears the cached delegate")
    void evictAllClearsCache() {
        decoder.evictAll();
        // no exception — cache is empty
    }

    @Nested
    @DisplayName("Clock Skew")
    class ClockSkewTest {

        @Test
        @DisplayName("accepts explicit clock skew")
        void acceptsExplicitClockSkew() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    INTERNAL_ISSUER, Duration.ofSeconds(30), cacheManager);
            d.evictAll();
        }

        @Test
        @DisplayName("treats null clock skew as zero")
        void nullClockSkewBecomesZero() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(INTERNAL_ISSUER, null, cacheManager);
            d.evictAll();
        }

        @Test
        @DisplayName("rejects invalid JWT even when clock skew is configured")
        void rejectsInvalidJwtWithClockSkew() {
            DynamicReactiveJwtDecoder d = new DynamicReactiveJwtDecoder(
                    INTERNAL_ISSUER, Duration.ofSeconds(60), cacheManager);

            StepVerifier.create(d.decode("not.a.jwt"))
                    .expectError(JwtException.class)
                    .verify();
        }
    }

    private static String unsignedToken(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".signature";
    }
}
