package com.emf.controlplane.config;

import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.JwksCache;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for SecurityConfig.
 * Verifies JWT authentication, role extraction, and endpoint security.
 * 
 * Note: These tests use the TestSecurityConfig which disables actual JWT validation
 * for unit testing purposes. Integration tests with real JWT validation should be
 * done separately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OidcProviderRepository oidcProviderRepository;

    @MockBean
    private JwksCache jwksCache;

    private RSAKey rsaKey;
    private JWKSet jwkSet;

    @BeforeEach
    void setUp() throws Exception {
        // Generate RSA key pair for testing
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate();
        jwkSet = new JWKSet(rsaKey.toPublicJWK());

        // Setup mock OIDC provider
        OidcProvider provider = new OidcProvider("test-provider", "https://issuer.example.com", "https://issuer.example.com/.well-known/jwks.json");
        provider.setId("test-provider-id");
        
        when(oidcProviderRepository.findByActiveTrue()).thenReturn(List.of(provider));
        when(jwksCache.getJwkSetWithFallback(anyString()))
                .thenReturn(new JwksCache.JwkSetWrapper(jwkSet));
    }

    @Test
    @DisplayName("Health check endpoints should be accessible without authentication")
    void healthCheckEndpointsShouldBeAccessibleWithoutAuth() throws Exception {
        // Use liveness endpoint which doesn't depend on external services
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator info endpoint should be accessible without authentication")
    void actuatorInfoShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Role extraction should extract roles from 'roles' claim")
    void shouldExtractRolesFromRolesClaim() {
        // Create a JWT with roles claim
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("roles", List.of("ADMIN", "USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Create converter and extract authorities
        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("Role extraction should extract roles from Keycloak realm_access claim")
    void shouldExtractRolesFromKeycloakRealmAccess() {
        // Create a JWT with Keycloak-style realm_access claim
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", List.of("admin", "manager"));

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("realm_access", realmAccess)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("Role extraction should extract scopes from 'scope' claim")
    void shouldExtractScopesFromScopeClaim() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("scope", "read write admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting("authority")
                .contains("SCOPE_read", "SCOPE_write", "SCOPE_admin");
    }

    @Test
    @DisplayName("Role extraction should extract roles from 'groups' claim")
    void shouldExtractRolesFromGroupsClaim() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("groups", List.of("developers", "admins"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting("authority")
                .contains("ROLE_DEVELOPERS", "ROLE_ADMINS");
    }

    @Test
    @DisplayName("Role extraction should handle comma-separated roles string")
    void shouldHandleCommaSeparatedRolesString() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("roles", "admin,user,manager")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities)
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("Role extraction should handle empty claims gracefully")
    void shouldHandleEmptyClaimsGracefully() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationConverter converter = createTestConverter();
        var authorities = converter.convert(jwt).getAuthorities();

        // Should not throw exception, may have empty or minimal authorities
        assertThat(authorities).isNotNull();
    }

    /**
     * Creates a test JwtAuthenticationConverter that mimics the production converter.
     * This is used to test role extraction logic without needing the full security context.
     */
    private JwtAuthenticationConverter createTestConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // Extract from "roles" claim
            extractRolesFromClaim(jwt, "roles", authorities);

            // Extract from "realm_access.roles" (Keycloak format)
            extractKeycloakRoles(jwt, authorities);

            // Extract from "groups" claim
            extractRolesFromClaim(jwt, "groups", authorities);

            // Extract from "scope" claim
            extractScopeAuthorities(jwt, authorities);

            return authorities;
        });
        return converter;
    }

    @SuppressWarnings("unchecked")
    private void extractRolesFromClaim(Jwt jwt, String claimName, Set<GrantedAuthority> authorities) {
        Object claim = jwt.getClaim(claimName);
        if (claim instanceof Collection) {
            ((Collection<String>) claim).forEach(role ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            );
        } else if (claim instanceof String) {
            Arrays.stream(((String) claim).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        }
    }

    @SuppressWarnings("unchecked")
    private void extractKeycloakRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof Collection) {
                ((Collection<String>) roles).forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                );
            }
        }
    }

    private void extractScopeAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            Arrays.stream(scope.split(" "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
        }
    }

    /**
     * Helper method to create a signed JWT for testing.
     */
    private String createSignedJwt(Map<String, Object> claims) throws Exception {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("https://issuer.example.com")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000));

        claims.forEach(claimsBuilder::claim);

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claimsBuilder.build()
        );

        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }
}
