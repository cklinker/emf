package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeltaTokenCustomizerTest {

    private KeltaTokenCustomizer customizer;

    @Mock
    private JwtEncodingContext context;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        customizer = new KeltaTokenCustomizer();
    }

    @Test
    void customize_addsClaimsToIdToken() {
        KeltaUserDetails user = new KeltaUserDetails(
                "user-1", "admin@test.com", "tenant-1", "profile-1",
                "System Administrator", "Test Admin", "$2a$10$hash",
                true, false, false
        );

        when(context.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));

        Map<String, Object> claimsMap = new HashMap<>();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("admin@test.com")
                .issuedAt(Instant.now());

        // Use a real builder that captures claims
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet claims = claimsBuilder.build();
        assertEquals("admin@test.com", claims.getClaim("email"));
        assertEquals("Test Admin", claims.getClaim("name"));
        assertEquals("tenant-1", claims.getClaim("tenant_id"));
        assertEquals("profile-1", claims.getClaim("profile_id"));
    }
}
