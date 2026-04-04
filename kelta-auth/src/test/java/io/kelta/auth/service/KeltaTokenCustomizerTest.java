package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeltaTokenCustomizerTest {

    private KeltaTokenCustomizer customizer;

    @Mock
    private JwtEncodingContext context;

    @Mock
    private Authentication authentication;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        customizer = new KeltaTokenCustomizer(jdbcTemplate);
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

    @Test
    void customize_enrichesAuthCodeAccessTokenWithConnectedAppClaims() {
        KeltaUserDetails user = new KeltaUserDetails(
                "user-1", "admin@test.com", "tenant-1", "profile-1",
                "System Administrator", "Test Admin", "$2a$10$hash",
                true, false, false
        );

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("third-party-app")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://third-party.com/callback")
                .build();

        when(context.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("access_token"));
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(registeredClient);

        // Mock connected_app lookup
        Map<String, Object> appRow = new HashMap<>();
        appRow.put("id", "connected-app-1");
        appRow.put("scopes", "[\"api\",\"read:records\"]");
        when(jdbcTemplate.queryForList(
                eq("SELECT id, scopes FROM connected_app WHERE client_id = ? AND active = true"),
                eq("third-party-app")
        )).thenReturn(List.of(appRow));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("admin@test.com")
                .issuedAt(Instant.now());
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet claims = claimsBuilder.build();
        assertEquals("admin@test.com", claims.getClaim("email"));
        assertEquals("tenant-1", claims.getClaim("tenant_id"));
        assertEquals("connected-app-1", claims.getClaim("connected_app_id"));
        assertEquals("[\"api\",\"read:records\"]", claims.getClaim("app_scopes"));
        assertEquals("connected_app", claims.getClaim("auth_method"));
    }

    @Test
    void customize_authCodeAccessTokenWithoutConnectedApp_usesNormalAuthMethod() {
        KeltaUserDetails user = new KeltaUserDetails(
                "user-1", "admin@test.com", "tenant-1", "profile-1",
                "System Administrator", "Test Admin", "$2a$10$hash",
                true, false, false
        );

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("kelta-platform")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:5173/auth/callback")
                .build();

        when(context.getPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("access_token"));
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(registeredClient);

        // No connected app for platform client
        when(jdbcTemplate.queryForList(
                eq("SELECT id, scopes FROM connected_app WHERE client_id = ? AND active = true"),
                eq("kelta-platform")
        )).thenReturn(List.of());

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("admin@test.com")
                .issuedAt(Instant.now());
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet claims = claimsBuilder.build();
        assertEquals("internal", claims.getClaim("auth_method"));
        assertNull(claims.getClaim("connected_app_id"));
    }
}
