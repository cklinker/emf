package com.emf.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that ValidationEngine validates required fields and returns appropriate errors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ValidationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testMissingRequiredFieldReturnsHttp400() {
        // Test that ValidationEngine validates required fields
        String url = "http://localhost:" + port + "/api/collections/projects";
        
        // Create project without required 'name' field
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("description", "Test project");
        projectData.put("status", "PLANNING");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectData, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        // Verify HTTP 400 for missing required fields
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test
    void testInvalidFieldTypeReturnsHttp400() {
        // Test that ValidationEngine validates field types
        String url = "http://localhost:" + port + "/api/collections/tasks";
        
        // Create task with invalid 'completed' field type (should be boolean)
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("title", "Test task");
        taskData.put("completed", "not-a-boolean"); // Invalid type
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(taskData, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        // Verify HTTP 400 for invalid field types
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test
    void testErrorResponseFollowsJsonApiFormat() {
        // Verify errors in JSON:API error format
        String url = "http://localhost:" + port + "/api/collections/projects";
        
        // Create project without required 'name' field
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("description", "Test project");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectData, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        // Verify error response follows JSON:API format
        String body = response.getBody();
        assertThat(body).isNotNull();
        
        // JSON:API error responses should have an "errors" field
        assertThat(body).contains("\"errors\"");
    }
    
    @Test
    void testValidRequestSucceeds() {
        // Test that valid requests are accepted
        String url = "http://localhost:" + port + "/api/collections/projects";
        
        // Create project with all required fields
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("name", "Test Project");
        projectData.put("description", "Test description");
        projectData.put("status", "PLANNING");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectData, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        // Verify successful creation
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
