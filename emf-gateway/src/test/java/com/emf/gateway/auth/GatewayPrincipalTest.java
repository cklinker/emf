package com.emf.gateway.auth;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GatewayPrincipal.
 */
class GatewayPrincipalTest {
    
    @Test
    void testConstructorWithValidData() {
        String username = "testuser";
        List<String> roles = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser", "email", "test@example.com");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertEquals(username, principal.getUsername());
        assertEquals(roles, principal.getRoles());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithNullUsername() {
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        assertThrows(NullPointerException.class, () -> {
            new GatewayPrincipal(null, roles, claims);
        });
    }
    
    @Test
    void testConstructorWithNullRoles() {
        String username = "testuser";
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, null, claims);
        
        assertEquals(username, principal.getUsername());
        assertTrue(principal.getRoles().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithNullClaims() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, null);
        
        assertEquals(username, principal.getUsername());
        assertEquals(roles, principal.getRoles());
        assertTrue(principal.getClaims().isEmpty());
    }
    
    @Test
    void testConstructorWithEmptyRoles() {
        String username = "testuser";
        List<String> roles = Collections.emptyList();
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertEquals(username, principal.getUsername());
        assertTrue(principal.getRoles().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithEmptyClaims() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Collections.emptyMap();
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertEquals(username, principal.getUsername());
        assertEquals(roles, principal.getRoles());
        assertTrue(principal.getClaims().isEmpty());
    }
    
    @Test
    void testHasRoleWithExistingRole() {
        String username = "testuser";
        List<String> roles = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertTrue(principal.hasRole("ADMIN"));
        assertTrue(principal.hasRole("USER"));
    }
    
    @Test
    void testHasRoleWithNonExistingRole() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertFalse(principal.hasRole("ADMIN"));
    }
    
    @Test
    void testHasRoleWithEmptyRoles() {
        String username = "testuser";
        List<String> roles = Collections.emptyList();
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertFalse(principal.hasRole("ADMIN"));
    }
    
    @Test
    void testRolesAreImmutable() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            principal.getRoles().add("ADMIN");
        });
    }
    
    @Test
    void testClaimsAreImmutable() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            principal.getClaims().put("newClaim", "value");
        });
    }
    
    @Test
    void testEqualsWithSameObject() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertEquals(principal, principal);
    }
    
    @Test
    void testEqualsWithEqualObjects() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal1 = new GatewayPrincipal(username, roles, claims);
        GatewayPrincipal principal2 = new GatewayPrincipal(username, roles, claims);
        
        assertEquals(principal1, principal2);
        assertEquals(principal1.hashCode(), principal2.hashCode());
    }
    
    @Test
    void testEqualsWithDifferentUsername() {
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal1 = new GatewayPrincipal("user1", roles, claims);
        GatewayPrincipal principal2 = new GatewayPrincipal("user2", roles, claims);
        
        assertNotEquals(principal1, principal2);
    }
    
    @Test
    void testEqualsWithDifferentRoles() {
        String username = "testuser";
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal1 = new GatewayPrincipal(username, List.of("USER"), claims);
        GatewayPrincipal principal2 = new GatewayPrincipal(username, List.of("ADMIN"), claims);
        
        assertNotEquals(principal1, principal2);
    }
    
    @Test
    void testEqualsWithDifferentClaims() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        
        GatewayPrincipal principal1 = new GatewayPrincipal(username, roles, Map.of("sub", "testuser"));
        GatewayPrincipal principal2 = new GatewayPrincipal(username, roles, Map.of("sub", "testuser", "email", "test@example.com"));
        
        assertNotEquals(principal1, principal2);
    }
    
    @Test
    void testEqualsWithNull() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertNotEquals(principal, null);
    }
    
    @Test
    void testEqualsWithDifferentClass() {
        String username = "testuser";
        List<String> roles = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        
        assertNotEquals(principal, "not a principal");
    }
    
    @Test
    void testToString() {
        String username = "testuser";
        List<String> roles = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser", "email", "test@example.com");
        
        GatewayPrincipal principal = new GatewayPrincipal(username, roles, claims);
        String toString = principal.toString();
        
        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("ADMIN"));
        assertTrue(toString.contains("USER"));
    }
}
