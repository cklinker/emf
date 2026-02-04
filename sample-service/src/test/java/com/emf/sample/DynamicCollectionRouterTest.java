package com.emf.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.emf.runtime.router.DynamicCollectionRouter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that DynamicCollectionRouter is auto-configured and provides CRUD endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DynamicCollectionRouterTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    void testDynamicCollectionRouterBeanExists() {
        // Verify that EmfRuntimeAutoConfiguration creates the router bean
        assertThat(context.containsBean("dynamicCollectionRouter")).isTrue();
        
        DynamicCollectionRouter router = context.getBean(DynamicCollectionRouter.class);
        assertThat(router).isNotNull();
    }
    
    @Test
    void testCrudEndpointsAreAvailable() {
        // Test that CRUD endpoints are available at /api/collections/{name}
        String baseUrl = "http://localhost:" + port + "/api/collections/projects";
        
        // GET endpoint should be available (even if it returns empty list)
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        
        // Should not be 404 - endpoint exists
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }
    
    @Test
    void testJsonApiResponseFormat() {
        // Verify JSON:API response format
        String baseUrl = "http://localhost:" + port + "/api/collections/projects";
        
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        
        // Response should contain JSON:API structure
        String body = response.getBody();
        assertThat(body).isNotNull();
        
        // JSON:API responses should have a "data" field
        assertThat(body).contains("\"data\"");
    }
}
