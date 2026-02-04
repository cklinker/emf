package com.emf.gateway.authz;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FieldPolicy.
 */
class FieldPolicyTest {
    
    @Test
    void shouldCreateFieldPolicyWithValidParameters() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        FieldPolicy policy = new FieldPolicy("email", "policy-1", roles);
        
        assertEquals("email", policy.getFieldName());
        assertEquals("policy-1", policy.getPolicyId());
        assertEquals(roles, policy.getRoles());
    }
    
    @Test
    void shouldThrowExceptionWhenFieldNameIsNull() {
        List<String> roles = Arrays.asList("ADMIN");
        
        assertThrows(NullPointerException.class, () -> {
            new FieldPolicy(null, "policy-1", roles);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenPolicyIdIsNull() {
        List<String> roles = Arrays.asList("ADMIN");
        
        assertThrows(NullPointerException.class, () -> {
            new FieldPolicy("email", null, roles);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenRolesIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new FieldPolicy("email", "policy-1", null);
        });
    }
    
    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        FieldPolicy policy1 = new FieldPolicy("email", "policy-1", roles);
        FieldPolicy policy2 = new FieldPolicy("email", "policy-1", roles);
        
        assertEquals(policy1, policy2);
        assertEquals(policy1.hashCode(), policy2.hashCode());
    }
    
    @Test
    void shouldNotBeEqualWhenFieldNameDiffers() {
        List<String> roles = Arrays.asList("ADMIN");
        FieldPolicy policy1 = new FieldPolicy("email", "policy-1", roles);
        FieldPolicy policy2 = new FieldPolicy("phone", "policy-1", roles);
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldNotBeEqualWhenPolicyIdDiffers() {
        List<String> roles = Arrays.asList("ADMIN");
        FieldPolicy policy1 = new FieldPolicy("email", "policy-1", roles);
        FieldPolicy policy2 = new FieldPolicy("email", "policy-2", roles);
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldNotBeEqualWhenRolesDiffer() {
        FieldPolicy policy1 = new FieldPolicy("email", "policy-1", Arrays.asList("ADMIN"));
        FieldPolicy policy2 = new FieldPolicy("email", "policy-1", Arrays.asList("USER"));
        
        assertNotEquals(policy1, policy2);
    }
    
    @Test
    void shouldHandleEmptyRolesList() {
        FieldPolicy policy = new FieldPolicy("email", "policy-1", Arrays.asList());
        
        assertNotNull(policy.getRoles());
        assertTrue(policy.getRoles().isEmpty());
    }
    
    @Test
    void shouldProduceReadableToString() {
        List<String> roles = Arrays.asList("ADMIN", "USER");
        FieldPolicy policy = new FieldPolicy("email", "policy-1", roles);
        
        String result = policy.toString();
        
        assertTrue(result.contains("email"));
        assertTrue(result.contains("policy-1"));
        assertTrue(result.contains("ADMIN"));
        assertTrue(result.contains("USER"));
    }
}
