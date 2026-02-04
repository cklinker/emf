package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyEvaluator.
 * 
 * Tests the evaluation of route and field policies against principals,
 * including OR logic for multiple roles and edge cases.
 */
class PolicyEvaluatorTest {
    
    private PolicyEvaluator evaluator;
    
    @BeforeEach
    void setUp() {
        evaluator = new PolicyEvaluator();
    }
    
    // ========== RoutePolicy Tests ==========
    
    @Test
    void evaluate_routePolicy_principalHasRequiredRole_returnsTrue() {
        // Given
        RoutePolicy policy = new RoutePolicy("GET", "policy-1", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void evaluate_routePolicy_principalLacksRequiredRole_returnsFalse() {
        // Given
        RoutePolicy policy = new RoutePolicy("GET", "policy-1", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_routePolicy_principalHasOneOfMultipleRoles_returnsTrue() {
        // Given - policy requires ADMIN or MODERATOR
        RoutePolicy policy = new RoutePolicy("POST", "policy-2", List.of("ADMIN", "MODERATOR"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("MODERATOR"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result, "Principal with MODERATOR should satisfy policy requiring ADMIN or MODERATOR");
    }
    
    @Test
    void evaluate_routePolicy_principalHasMultipleRolesIncludingRequired_returnsTrue() {
        // Given
        RoutePolicy policy = new RoutePolicy("DELETE", "policy-3", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER", "ADMIN", "MODERATOR"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void evaluate_routePolicy_principalHasNoneOfMultipleRequiredRoles_returnsFalse() {
        // Given - policy requires ADMIN or MODERATOR
        RoutePolicy policy = new RoutePolicy("PUT", "policy-4", List.of("ADMIN", "MODERATOR"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER", "GUEST"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_routePolicy_policyHasNoRoles_returnsTrue() {
        // Given - policy with empty roles list
        RoutePolicy policy = new RoutePolicy("GET", "policy-5", Collections.emptyList());
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result, "Policy with no roles should be satisfied by any principal");
    }
    
    @Test
    void evaluate_routePolicy_principalHasNoRoles_returnsFalse() {
        // Given
        RoutePolicy policy = new RoutePolicy("POST", "policy-6", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", Collections.emptyList(), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_routePolicy_nullPolicy_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.evaluate((RoutePolicy) null, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_routePolicy_nullPrincipal_returnsFalse() {
        // Given
        RoutePolicy policy = new RoutePolicy("GET", "policy-7", List.of("ADMIN"));
        
        // When
        boolean result = evaluator.evaluate(policy, null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_routePolicy_roleCaseSensitive_returnsFalse() {
        // Given - role names are case-sensitive
        RoutePolicy policy = new RoutePolicy("GET", "policy-8", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("admin"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result, "Role matching should be case-sensitive");
    }
    
    // ========== FieldPolicy Tests ==========
    
    @Test
    void evaluate_fieldPolicy_principalHasRequiredRole_returnsTrue() {
        // Given
        FieldPolicy policy = new FieldPolicy("email", "policy-1", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void evaluate_fieldPolicy_principalLacksRequiredRole_returnsFalse() {
        // Given
        FieldPolicy policy = new FieldPolicy("salary", "policy-2", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_fieldPolicy_principalHasOneOfMultipleRoles_returnsTrue() {
        // Given - policy requires ADMIN or HR
        FieldPolicy policy = new FieldPolicy("salary", "policy-3", List.of("ADMIN", "HR"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("HR"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result, "Principal with HR should satisfy policy requiring ADMIN or HR");
    }
    
    @Test
    void evaluate_fieldPolicy_principalHasMultipleRolesIncludingRequired_returnsTrue() {
        // Given
        FieldPolicy policy = new FieldPolicy("ssn", "policy-4", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER", "ADMIN", "HR"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void evaluate_fieldPolicy_principalHasNoneOfMultipleRequiredRoles_returnsFalse() {
        // Given - policy requires ADMIN or HR
        FieldPolicy policy = new FieldPolicy("salary", "policy-5", List.of("ADMIN", "HR"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER", "GUEST"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_fieldPolicy_policyHasNoRoles_returnsTrue() {
        // Given - policy with empty roles list
        FieldPolicy policy = new FieldPolicy("name", "policy-6", Collections.emptyList());
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertTrue(result, "Policy with no roles should be satisfied by any principal");
    }
    
    @Test
    void evaluate_fieldPolicy_principalHasNoRoles_returnsFalse() {
        // Given
        FieldPolicy policy = new FieldPolicy("email", "policy-7", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", Collections.emptyList(), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_fieldPolicy_nullPolicy_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.evaluate((FieldPolicy) null, principal);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_fieldPolicy_nullPrincipal_returnsFalse() {
        // Given
        FieldPolicy policy = new FieldPolicy("email", "policy-8", List.of("ADMIN"));
        
        // When
        boolean result = evaluator.evaluate(policy, null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void evaluate_fieldPolicy_roleCaseSensitive_returnsFalse() {
        // Given - role names are case-sensitive
        FieldPolicy policy = new FieldPolicy("email", "policy-9", List.of("ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("admin"), Map.of());
        
        // When
        boolean result = evaluator.evaluate(policy, principal);
        
        // Then
        assertFalse(result, "Role matching should be case-sensitive");
    }
    
    // ========== hasRole Tests ==========
    
    @Test
    void hasRole_principalHasRole_returnsTrue() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN", "USER"), Map.of());
        
        // When
        boolean result = evaluator.hasRole(principal, "ADMIN");
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void hasRole_principalLacksRole_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        
        // When
        boolean result = evaluator.hasRole(principal, "ADMIN");
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void hasRole_nullPrincipal_returnsFalse() {
        // When
        boolean result = evaluator.hasRole(null, "ADMIN");
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void hasRole_nullRole_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.hasRole(principal, null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void hasRole_caseSensitive_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("ADMIN"), Map.of());
        
        // When
        boolean result = evaluator.hasRole(principal, "admin");
        
        // Then
        assertFalse(result, "Role matching should be case-sensitive");
    }
    
    @Test
    void hasRole_emptyRolesList_returnsFalse() {
        // Given
        GatewayPrincipal principal = new GatewayPrincipal("user1", Collections.emptyList(), Map.of());
        
        // When
        boolean result = evaluator.hasRole(principal, "ADMIN");
        
        // Then
        assertFalse(result);
    }
}
