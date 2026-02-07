package com.emf.gateway.integration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Base class for integration tests that require the full Docker environment.
 * 
 * This is a plain JUnit test class (NOT a Spring Boot test) that makes HTTP
 * requests to the running Docker services.
 * 
 * Provides:
 * - Service URL constants for all platform services
 * - Service health check utilities
 * - Lifecycle methods for test setup and teardown
 * - Awaitility-based service readiness verification
 * 
 * Subclasses should implement cleanupTestData() to clean up any test resources.
 * 
 * Note: This test does NOT start the Gateway application. It tests against
 * the running Gateway Docker container at http://localhost:8080.
 * 
 * Validates: Requirements 14.4
 */
@Tag("integration")
public abstract class IntegrationTestBase {
    
    // Service URL constants
    protected static final String GATEWAY_URL = "http://localhost:8080";
    protected static final String CONTROL_PLANE_URL = "http://localhost:8081";
    protected static final String SAMPLE_SERVICE_URL = "http://localhost:8082";
    protected static final String KEYCLOAK_URL = "http://localhost:8180";
    
    // Test utilities
    protected RestTemplate restTemplate;
    protected AuthenticationHelper authHelper;
    protected TestDataHelper testDataHelper;
    
    /**
     * Wait for all services to be healthy before running any tests.
     * Uses Awaitility to poll service health endpoints with timeout.
     */
    @BeforeAll
    public static void waitForServices() {
        Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> {
                return isServiceHealthy(GATEWAY_URL) &&
                       isServiceHealthy(CONTROL_PLANE_URL) &&
                       isServiceHealthy(SAMPLE_SERVICE_URL);
            });
    }
    
    /**
     * Set up test utilities before each test.
     */
    @BeforeEach
    public void setUp() {
        // Create RestTemplate with HttpComponentsClientHttpRequestFactory to support PATCH
        // This uses Apache HttpClient which properly supports all HTTP methods including PATCH
        org.springframework.http.client.HttpComponentsClientHttpRequestFactory requestFactory = 
            new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        restTemplate = new RestTemplate(requestFactory);
        
        authHelper = new AuthenticationHelper();
        testDataHelper = new TestDataHelper(restTemplate, authHelper);
    }
    
    /**
     * Clean up test data after each test.
     * Subclasses should override this method to clean up their specific test data.
     */
    @AfterEach
    public void tearDown() {
        cleanupTestData();
    }
    
    /**
     * Check if a service is healthy by calling its health endpoint.
     * 
     * @param baseUrl The base URL of the service
     * @return true if the service is healthy, false otherwise
     */
    protected static boolean isServiceHealthy(String baseUrl) {
        try {
            RestTemplate template = new RestTemplate();
            ResponseEntity<String> response = template.getForEntity(
                baseUrl + "/actuator/health",
                String.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            // Service is not reachable or not healthy
            return false;
        }
    }
    
    /**
     * Clean up test data created during the test.
     * Subclasses should override this method to implement their cleanup logic.
     */
    protected void cleanupTestData() {
        // Default implementation does nothing
        // Subclasses should override to clean up their test data
    }
}
