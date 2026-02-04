package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test for the test framework base classes.
 * 
 * Tests that:
 * - IntegrationTestBase properly initializes
 * - AuthenticationHelper can be autowired
 * - TestDataHelper can be autowired
 * - Service health checks work
 * - Authentication helper methods work
 */
class TestFrameworkVerificationTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Test
    void testIntegrationTestBase_Initializes() {
        // Verify that the base class initialized properly
        assertNotNull(restTemplate, "RestTemplate should be initialized");
    }
    
    @Test
    void testServiceHealthChecks_AllServicesHealthy() {
        // Verify that all services are healthy
        assertTrue(isServiceHealthy(GATEWAY_URL), "Gateway should be healthy");
        assertTrue(isServiceHealthy(CONTROL_PLANE_URL), "Control Plane should be healthy");
        assertTrue(isServiceHealthy(SAMPLE_SERVICE_URL), "Sample Service should be healthy");
    }
    
    @Test
    void testAuthenticationHelper_CanBeAutowired() {
        // Verify that AuthenticationHelper can be autowired
        assertNotNull(authHelper, "AuthenticationHelper should be autowired");
    }
    
    @Test
    void testAuthenticationHelper_CanGetAdminToken() {
        // Verify that we can get an admin token
        String token = authHelper.getAdminToken();
        assertNotNull(token, "Admin token should not be null");
        assertFalse(token.isEmpty(), "Admin token should not be empty");
    }
    
    @Test
    void testAuthenticationHelper_CanGetUserToken() {
        // Verify that we can get a user token
        String token = authHelper.getUserToken();
        assertNotNull(token, "User token should not be null");
        assertFalse(token.isEmpty(), "User token should not be empty");
    }
    
    @Test
    void testAuthenticationHelper_CanCreateAuthHeaders() {
        // Verify that we can create auth headers
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        assertNotNull(headers, "Headers should not be null");
        assertTrue(headers.containsKey("Authorization"), "Headers should contain Authorization");
        assertTrue(headers.containsKey("Content-Type"), "Headers should contain Content-Type");
    }
    
    @Test
    void testTestDataHelper_CanBeAutowired() {
        // Verify that TestDataHelper can be autowired
        assertNotNull(testDataHelper, "TestDataHelper should be autowired");
    }
    
    @Override
    protected void cleanupTestData() {
        // Clean up any test data created by this test
        if (testDataHelper != null) {
            testDataHelper.cleanupAll();
        }
    }
}
