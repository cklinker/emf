package io.kelta.gateway.auth;

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
        List<String> groups = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser", "email", "test@example.com");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertEquals(username, principal.getUsername());
        assertEquals(groups, principal.getGroups());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithNullUsername() {
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        assertThrows(NullPointerException.class, () -> {
            new GatewayPrincipal(null, groups, claims);
        });
    }
    
    @Test
    void testConstructorWithNullGroups() {
        String username = "testuser";
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, null, claims);

        assertEquals(username, principal.getUsername());
        assertTrue(principal.getGroups().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithNullClaims() {
        String username = "testuser";
        List<String> groups = List.of("USER");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, null);

        assertEquals(username, principal.getUsername());
        assertEquals(groups, principal.getGroups());
        assertTrue(principal.getClaims().isEmpty());
    }
    
    @Test
    void testConstructorWithEmptyGroups() {
        String username = "testuser";
        List<String> groups = Collections.emptyList();
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertEquals(username, principal.getUsername());
        assertTrue(principal.getGroups().isEmpty());
        assertEquals(claims, principal.getClaims());
    }
    
    @Test
    void testConstructorWithEmptyClaims() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Collections.emptyMap();

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertEquals(username, principal.getUsername());
        assertEquals(groups, principal.getGroups());
        assertTrue(principal.getClaims().isEmpty());
    }
    
    @Test
    void testHasGroupWithExistingGroup() {
        String username = "testuser";
        List<String> groups = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertTrue(principal.hasGroup("ADMIN"));
        assertTrue(principal.hasGroup("USER"));
    }
    
    @Test
    void testHasGroupWithNonExistingGroup() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertFalse(principal.hasGroup("ADMIN"));
    }
    
    @Test
    void testHasGroupWithEmptyGroups() {
        String username = "testuser";
        List<String> groups = Collections.emptyList();
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertFalse(principal.hasGroup("ADMIN"));
    }
    
    @Test
    void testGroupsAreImmutable() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertThrows(UnsupportedOperationException.class, () -> {
            principal.getGroups().add("ADMIN");
        });
    }
    
    @Test
    void testClaimsAreImmutable() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertThrows(UnsupportedOperationException.class, () -> {
            principal.getClaims().put("newClaim", "value");
        });
    }
    
    @Test
    void testEqualsWithSameObject() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertEquals(principal, principal);
    }

    @Test
    void testEqualsWithEqualObjects() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal1 = new GatewayPrincipal(username, groups, claims);
        GatewayPrincipal principal2 = new GatewayPrincipal(username, groups, claims);

        assertEquals(principal1, principal2);
        assertEquals(principal1.hashCode(), principal2.hashCode());
    }

    @Test
    void testEqualsWithDifferentUsername() {
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal1 = new GatewayPrincipal("user1", groups, claims);
        GatewayPrincipal principal2 = new GatewayPrincipal("user2", groups, claims);

        assertNotEquals(principal1, principal2);
    }

    @Test
    void testEqualsWithDifferentGroups() {
        String username = "testuser";
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal1 = new GatewayPrincipal(username, List.of("USER"), claims);
        GatewayPrincipal principal2 = new GatewayPrincipal(username, List.of("ADMIN"), claims);

        assertNotEquals(principal1, principal2);
    }

    @Test
    void testEqualsWithDifferentClaims() {
        String username = "testuser";
        List<String> groups = List.of("USER");

        GatewayPrincipal principal1 = new GatewayPrincipal(username, groups, Map.of("sub", "testuser"));
        GatewayPrincipal principal2 = new GatewayPrincipal(username, groups, Map.of("sub", "testuser", "email", "test@example.com"));

        assertNotEquals(principal1, principal2);
    }

    @Test
    void testEqualsWithNull() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertNotEquals(principal, null);
    }

    @Test
    void testEqualsWithDifferentClass() {
        String username = "testuser";
        List<String> groups = List.of("USER");
        Map<String, Object> claims = Map.of("sub", "testuser");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);

        assertNotEquals(principal, "not a principal");
    }

    @Test
    void testToString() {
        String username = "testuser";
        List<String> groups = List.of("ADMIN", "USER");
        Map<String, Object> claims = Map.of("sub", "testuser", "email", "test@example.com");

        GatewayPrincipal principal = new GatewayPrincipal(username, groups, claims);
        String toString = principal.toString();

        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("ADMIN"));
        assertTrue(toString.contains("USER"));
    }
}
