package com.emf.gateway.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Test configuration for integration tests.
 * 
 * Provides beans needed by integration test helper components:
 * - RestTemplate for making HTTP requests to services
 */
@TestConfiguration
public class IntegrationTestConfig {
    
    /**
     * Create a RestTemplate bean for integration tests.
     * Used by TestDataHelper and AuthenticationHelper to make HTTP requests.
     * 
     * @return RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
