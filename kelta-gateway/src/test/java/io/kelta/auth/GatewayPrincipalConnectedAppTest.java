package io.kelta.gateway.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrincipalExtractor — Connected App Tokens")
class GatewayPrincipalConnectedAppTest {

    private final PrincipalExtractor extractor = new PrincipalExtractor();

    @Test
    @DisplayName("Should detect connected app token with auth_method=api_key")
    void shouldDetectConnectedAppToken() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "cid-123")
                .claim("preferred_username", "cid-123")
                .claim("auth_method", "api_key")
                .claim("connected_app_id", "app-456")
                .claim("app_scopes", "read:contacts,write:contacts")
                .claim("tenant_id", "t-789")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        GatewayPrincipal principal = extractor.extractPrincipal(jwt);

        assertThat(principal.isConnectedApp()).isTrue();
        assertThat(principal.getConnectedAppId()).isEqualTo("app-456");
        assertThat(principal.getAppScopes()).isEqualTo("read:contacts,write:contacts");
        assertThat(principal.getTenantId()).isEqualTo("t-789");
    }

    @Test
    @DisplayName("Should NOT detect connected app for user tokens")
    void shouldNotDetectForUserTokens() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user@example.com")
                .claim("preferred_username", "user@example.com")
                .claim("auth_method", "internal")
                .claim("tenant_id", "t-789")
                .claim("profile_id", "prof-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        GatewayPrincipal principal = extractor.extractPrincipal(jwt);

        assertThat(principal.isConnectedApp()).isFalse();
        assertThat(principal.getConnectedAppId()).isNull();
        assertThat(principal.getAppScopes()).isNull();
        assertThat(principal.getProfileId()).isEqualTo("prof-1");
    }

    @Test
    @DisplayName("Should handle missing auth_method claim gracefully")
    void shouldHandleMissingAuthMethod() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user@example.com")
                .claim("preferred_username", "user@example.com")
                .claim("tenant_id", "t-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        GatewayPrincipal principal = extractor.extractPrincipal(jwt);

        assertThat(principal.isConnectedApp()).isFalse();
    }
}
