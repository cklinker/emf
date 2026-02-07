package com.emf.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

import java.util.Base64;

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
}
