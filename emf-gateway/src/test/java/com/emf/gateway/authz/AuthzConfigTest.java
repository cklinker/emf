package com.emf.gateway.authz;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthzConfig.
 */
class AuthzConfigTest {
    
    @Test
    void shouldCreateAuthzConfigWithValidParameters() {
        List<RoutePolicy> routePolicies = Arrays.asList(
            new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"))
        );
        List<FieldPolicy> fieldPolicies = Arrays.asList(
            new FieldPolicy("email", "policy-2", Arrays.asList("ADMIN"))
        );
        
        AuthzConfig config = new AuthzConfig("users-collection", routePolicies, fieldPolicies);
        
        assertEquals("users-collection", config.getCollectionId());
        assertEquals(routePolicies, config.getRoutePolicies());
        assertEquals(fieldPolicies, config.getFieldPolicies());
    }
    
    @Test
    void shouldThrowExceptionWhenCollectionIdIsNull() {
        List<RoutePolicy> routePolicies = Collections.emptyList();
        List<FieldPolicy> fieldPolicies = Collections.emptyList();
        
        assertThrows(NullPointerException.class, () -> {
            new AuthzConfig(null, routePolicies, fieldPolicies);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenRoutePoliciesIsNull() {
        List<FieldPolicy> fieldPolicies = Collections.emptyList();
        
        assertThrows(NullPointerException.class, () -> {
            new AuthzConfig("users-collection", null, fieldPolicies);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenFieldPoliciesIsNull() {
        List<RoutePolicy> routePolicies = Collections.emptyList();
        
        assertThrows(NullPointerException.class, () -> {
            new AuthzConfig("users-collection", routePolicies, null);
        });
    }
    
    @Test
    void shouldHandleEmptyPolicyLists() {
        AuthzConfig config = new AuthzConfig(
            "users-collection",
            Collections.emptyList(),
            Collections.emptyList()
        );
        
        assertNotNull(config.getRoutePolicies());
        assertNotNull(config.getFieldPolicies());
        assertTrue(config.getRoutePolicies().isEmpty());
        assertTrue(config.getFieldPolicies().isEmpty());
    }
    
    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        List<RoutePolicy> routePolicies = Arrays.asList(
            new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"))
        );
        List<FieldPolicy> fieldPolicies = Arrays.asList(
            new FieldPolicy("email", "policy-2", Arrays.asList("ADMIN"))
        );
        
        AuthzConfig config1 = new AuthzConfig("users-collection", routePolicies, fieldPolicies);
        AuthzConfig config2 = new AuthzConfig("users-collection", routePolicies, fieldPolicies);
        
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }
    
    @Test
    void shouldNotBeEqualWhenCollectionIdDiffers() {
        List<RoutePolicy> routePolicies = Collections.emptyList();
        List<FieldPolicy> fieldPolicies = Collections.emptyList();
        
        AuthzConfig config1 = new AuthzConfig("users-collection", routePolicies, fieldPolicies);
        AuthzConfig config2 = new AuthzConfig("posts-collection", routePolicies, fieldPolicies);
        
        assertNotEquals(config1, config2);
    }
    
    @Test
    void shouldNotBeEqualWhenRoutePoliciesDiffer() {
        List<FieldPolicy> fieldPolicies = Collections.emptyList();
        
        AuthzConfig config1 = new AuthzConfig(
            "users-collection",
            Arrays.asList(new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"))),
            fieldPolicies
        );
        AuthzConfig config2 = new AuthzConfig(
            "users-collection",
            Arrays.asList(new RoutePolicy("POST", "policy-2", Arrays.asList("USER"))),
            fieldPolicies
        );
        
        assertNotEquals(config1, config2);
    }
    
    @Test
    void shouldNotBeEqualWhenFieldPoliciesDiffer() {
        List<RoutePolicy> routePolicies = Collections.emptyList();
        
        AuthzConfig config1 = new AuthzConfig(
            "users-collection",
            routePolicies,
            Arrays.asList(new FieldPolicy("email", "policy-1", Arrays.asList("ADMIN")))
        );
        AuthzConfig config2 = new AuthzConfig(
            "users-collection",
            routePolicies,
            Arrays.asList(new FieldPolicy("phone", "policy-2", Arrays.asList("USER")))
        );
        
        assertNotEquals(config1, config2);
    }
    
    @Test
    void shouldProduceReadableToString() {
        List<RoutePolicy> routePolicies = Arrays.asList(
            new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN")),
            new RoutePolicy("POST", "policy-2", Arrays.asList("USER"))
        );
        List<FieldPolicy> fieldPolicies = Arrays.asList(
            new FieldPolicy("email", "policy-3", Arrays.asList("ADMIN"))
        );
        
        AuthzConfig config = new AuthzConfig("users-collection", routePolicies, fieldPolicies);
        String result = config.toString();
        
        assertTrue(result.contains("users-collection"));
        assertTrue(result.contains("2 policies"));
        assertTrue(result.contains("1 policies"));
    }
}
