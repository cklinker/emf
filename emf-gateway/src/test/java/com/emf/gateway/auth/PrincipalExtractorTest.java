package com.emf.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrincipalExtractor.
 * Tests extraction of username, roles, and claims from JWT tokens.
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
        assertEquals(List.of("ADMIN", "USER"), principal.getRoles());
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
        assertEquals(List.of("USER"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithAuthoritiesClaim() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "authorities", List.of("ADMIN", "USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithRolesPreferredOverAuthorities() {
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
        assertEquals(List.of("ADMIN"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithNoRoles() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getRoles().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithEmptyRolesList() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of(),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getRoles().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithRolesAsCommaSeparatedString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", "ADMIN,USER,MODERATOR",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER", "MODERATOR"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithAuthoritiesAsCommaSeparatedString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "authorities", "ADMIN,USER",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithEmptyRolesString() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", "",
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getRoles().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithSingleRole() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", List.of("USER"),
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("USER"), principal.getRoles());
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
    void testExtractPrincipalWithInvalidRolesType() {
        Map<String, Object> claims = Map.of(
            "preferred_username", "testuser",
            "sub", "user-id-123",
            "roles", 123, // Invalid type
            "email", "test@example.com"
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertTrue(principal.getRoles().isEmpty()); // Should handle gracefully
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testExtractPrincipalWithMixedTypeRolesList() {
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
        assertEquals(List.of("ADMIN", "USER"), principal.getRoles());
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
            "aud", "emf-gateway",
            "exp", 1234567890,
            "iat", 1234567800
        );
        
        Jwt jwt = createJwt(claims);
        GatewayPrincipal principal = extractor.extractPrincipal(jwt);
        
        assertEquals("testuser", principal.getUsername());
        assertEquals(List.of("ADMIN", "USER"), principal.getRoles());
        assertEquals(claims, principal.getClaims());
        assertEquals("test@example.com", principal.getClaims().get("email"));
        assertEquals("Test User", principal.getClaims().get("name"));
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
