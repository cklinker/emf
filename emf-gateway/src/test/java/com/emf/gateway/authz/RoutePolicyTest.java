package com.emf.gateway.authz;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoutePolicy.
 */
class RoutePolicyTest {
    
    @Test
    void shouldCreateRoutePolicyWithValidParameters() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        RoutePolicy policy = new RoutePolicy("GET", "policy-1", roles);
        
        assertEquals("GET", policy.getMethod());
        assertEquals("policy-1", policy.getPolicyId());
        assertEquals(roles, policy.getRoles());
    }
    
    @Test
    void shouldThrowExceptionWhenMethodIsNull() {
        List<String> roles = Arrays.asList("ADMIN");
        
        assertThrows(NullPointerException.class, () -> {
            new RoutePolicy(null, "policy-1", roles);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenPolicyIdIsNull() {
        List<String> roles = Arrays.asList("ADMIN");
        
        assertThrows(NullPointerException.class, () -> {
            new RoutePolicy("GET", null, roles);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenRolesIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new RoutePolicy("GET", "policy-1", null);
        });
    }
    
    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        RoutePolicy policy1 = new RoutePolicy("GET", "policy-1", roles);
        RoutePolicy policy2 = new RoutePolicy("GET", "policy-1", roles);
        
        assertEquals(policy1, policy2);
        assertEquals(policy1.hashCode(), policy2.hashCode());
    }
    
    @Test
    void shouldNotBeEqualWhenMethodDiffers() {
        List<String> roles = Arrays.asList("ADMIN");
        RoutePolicy policy1 = new RoutePolicy("GET", "policy-1", roles);
        RoutePolicy policy2 = new RoutePolicy("POST", "policy-1", roles);
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldNotBeEqualWhenPolicyIdDiffers() {
        List<String> roles = Arrays.asList("ADMIN");
        RoutePolicy policy1 = new RoutePolicy("GET", "policy-1", roles);
        RoutePolicy policy2 = new RoutePolicy("GET", "policy-2", roles);
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldNotBeEqualWhenRolesDiffer() {
        RoutePolicy policy1 = new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"));
        RoutePolicy policy2 = new RoutePolicy("GET", "policy-1", Arrays.asList("USER"));
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldHandleEmptyRolesList() {
        RoutePolicy policy = new RoutePolicy("GET", "policy-1", Arrays.asList());
        
        assertNotNull(policy.getRoles());
        assertTrue(policy.getRoles().isEmpty());
    }
    
    @Test
    void shouldProduceReadableToString() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        RoutePolicy policy = new RoutePolicy("POST", "policy-1", roles);
        
        String result = policy.toString();
        
        assertTrue(result.contains("POST"));
        assertTrue(result.contains("policy-1"));
        assertTrue(result.contains("ADMIN"));
        assertTrue(result.contains("USER"));
    }
}
