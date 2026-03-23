package io.kelta.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrincipalExtractor.
 * Tests extraction of username, groups, and claims from JWT tokens.
 */
class PrincipalExtractorTest {
    
    private PrincipalExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new PrincipalExtractor();
    }
    
    @Test
    void testExtractPrincipalWithPreferredUsername() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("ADMIN", "USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithSubFallback() {
        Map<String, Object> claims = Map.of(
            "sub", "user-id-123",
            "roles", List.of("USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("user-id-123", principal.getUsername());
        assertEquals(List.of("USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithAuthoritiesClaimAsGroups() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "authorities", List.of("ADMIN", "USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithRolesClaimPreferredOverAuthorities() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("ADMIN"),
            "authorities", List.of("USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithNoGroups() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getGroups().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithEmptyGroupsList() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of(),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getGroups().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithGroupsAsCommaSeparatedString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", "ADMIN,USER,MODERATOR",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER", "MODERATOR"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithAuthoritiesAsCommaSeparatedGroupsString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "authorities", "ADMIN,USER",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithEmptyGroupsString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", "",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getGroups().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithSingleGroup() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithEmptyPreferredUsername() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "",
            "sub", "user-id-123",
            "roles", List.of("USER")
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        // Should fall back to sub when preferred_username is empty
        assertEquals("user-id-123", principal.getUsername());
    }
    
    @Test
    void testExtractPrincipalWithNullJwt() {
        assertThrows(IllegalArgumentException.class, () -> {
            extractor.extractPrincipal(null);
        });
    }
    
    @Test
    void testExtractPrincipalWithMissingUsernameAndSub() {
        Map<String, Object> claims = Map.of(
            "roles", List.of("USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        
        assertThrows(IllegalArgumentException.class, () -> {
            extractor.extractPrincipal(jwt);
        });
    }
    
    @Test
    void testExtractPrincipalWithEmptyUsernameAndSub() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "",
            "sub", "",
            "roles", List.of("USER")
        );
        
        Jwt jwt = createJwt(claims);
        
        assertThrows(IllegalArgumentException.class, () -> {
            extractor.extractPrincipal(jwt);
        });
    }
    
    @Test
    void testExtractPrincipalWithInvalidGroupsType() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", 123, // Invalid type
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getGroups().isEmpty()); // Should handle gracefully with invalid type
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithMixedTypeGroupsList() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("ADMIN", 123, "USER"), // Mixed types
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        // Should only extract string values
        assertEquals(List.of("ADMIN", "USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithComplexClaims() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("ADMIN", "USER"),
            "email", "test@example.com",
            "name", "Test User",
            "iss", "https://auth.example.com",
            "aud", "kelta-gateway",
            "exp", 1234567890,
            "iat", 1234567800
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getGroups());
        assertEquals(claims, principal.getClaims());
        assertEquals("test@example.com", principal.getClaims().get("email"));
        assertEquals("Test User", principal.getClaims().get("name"));
    }
    
    @Test
    void testExtractPrincipalWithKeltaAuthClaims() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "user@example.com",
            "sub", "user-uuid",
            "email", "user@example.com",
            "tenant_id", "tenant-123",
            "profile_id", "profile-456",
            "profile_name", "System Administrator",
            "auth_method", "internal"
        );

        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);

        assertEquals("user@example.com", principal.getUsername());
        assertEquals("tenant-123", principal.getTenantId());
        assertEquals("profile-456", principal.getProfileId());
        assertEquals("System Administrator", principal.getProfileName());
    }

    @Test
    void testExtractPrincipalWithoutKeltaAuthClaims() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "user@example.com",
            "sub", "user-uuid",
            "roles", List.of("admin")
        );

        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);

        assertEquals("user@example.com", principal.getUsername());
        assertNull(principal.getTenantId());
        assertNull(principal.getProfileId());
        assertNull(principal.getProfileName());
    }

    /**
     * Helper method to create a JWT with the given claims.
     */
    private Jwt createJwt(Map<String, Object> claims) {
        return new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "RS256", "typ", "JWT"),
            claims
        );
    }
}
