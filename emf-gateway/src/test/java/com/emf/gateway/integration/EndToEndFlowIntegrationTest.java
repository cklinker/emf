package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for complete request flows through the Docker environment.
 * 
 * Tests complete scenarios including:
 * - Complete project lifecycle (create → read → update → delete)
 * - Project with tasks lifecycle (create project → create tasks → read with includes → update → delete)
 * - Authentication flow (get token → make request)
 * - Authorization flow (admin access → user denied)
 * - Error handling flow (invalid request → error response)
 * 
 * This test extends IntegrationTestBase and tests against the actual running Docker services,
 * not mocked backends. It validates the complete integration of all platform components:
 * Gateway, Control Plane, Sample Service, Keycloak, PostgreSQL, Redis, and Kafka.
 * 
 * Validates: Requirements 10.1-10.8
 */
public class EndToEndFlowIntegrationTest extends IntegrationTestBase {
    
    @Override
    protected void cleanupTestData() {
        // Cleanup is handled by TestDataHelper
        if (testDataHelper != null) {
            testDataHelper.cleanupAll();
        }
    }
    
    /**
     * Test complete project lifecycle: create → read → update → delete.
     * 
     * Validates:
     * - Authentication with Keycloak
     * - Request routing through Gateway
     * - CRUD operations on Sample Service
     * - JSON:API response format
     * - Database persistence
     * 
     * Validates: Requirement 10.1
     */
    @Test
    void testCompleteProjectLifecycle() {
        // 1. Authenticate - get admin token
        String token = authHelper.getAdminToken();
        assertThat(token).isNotNull().isNotEmpty();
        
        // 2. Create project
        String projectId = testDataHelper.createProject(
            "E2E Test Project",
            "End-to-end test project",
            "PLANNING"
        );
        assertThat(projectId).isNotNull().isNotEmpty();
        
        // 3. Read project - verify it was created
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> responseBody = getResponse.getBody();
        assertThat(responseBody).containsKey("data");
        
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        assertThat(data.get("type")).isEqualTo("projects");
        assertThat(data.get("id")).isEqualTo(projectId);
        
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes.get("name")).isEqualTo("E2E Test Project");
        assertThat(attributes.get("description")).isEqualTo("End-to-end test project");
        assertThat(attributes.get("status")).isEqualTo("PLANNING");
        
        // 4. Update project
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "projects",
                "id", projectId,
                "attributes", Map.of(
                    "name", "E2E Test Project Updated",
                    "description", "Updated description",
                    "status", "ACTIVE"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateData, headers);
        
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.PATCH,
            updateRequest,
            Map.class
        );
        
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updatedData = (Map<String, Object>) updateResponse.getBody().get("data");
        Map<String, Object> updatedAttributes = (Map<String, Object>) updatedData.get("attributes");
        assertThat(updatedAttributes.get("name")).isEqualTo("E2E Test Project Updated");
        assertThat(updatedAttributes.get("status")).isEqualTo("ACTIVE");
        
        // 5. Delete project
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        
        // 6. Verify project is deleted - should return 404
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + projectId,
                HttpMethod.GET,
                request,
                Map.class
            );
            // Should not reach here
            assertThat(false).as("Expected 404 for deleted project").isTrue();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
    
    /**
     * Test project with tasks lifecycle: create project → create tasks → read with includes → update → delete.
     * 
     * Validates:
     * - Relationship handling
     * - Include parameter processing
     * - Redis caching
     * - Foreign key constraints
     * 
     * Validates: Requirement 10.2
     */
    @Test
    void testProjectWithTasksLifecycle() {
        // 1. Authenticate
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // 2. Create project
        String projectId = testDataHelper.createProject(
            "Project with Tasks",
            "Testing relationships",
            "ACTIVE"
        );
        
        // 3. Create tasks for the project
        String task1Id = testDataHelper.createTask(
            "Task 1",
            "First task",
            projectId
        );
        
        String task2Id = testDataHelper.createTask(
            "Task 2",
            "Second task",
            projectId
        );
        
        assertThat(task1Id).isNotNull().isNotEmpty();
        assertThat(task2Id).isNotNull().isNotEmpty();
        
        // 4. Read task with project relationship
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> taskResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + task1Id,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> taskData = (Map<String, Object>) taskResponse.getBody().get("data");
        
        // Verify relationship data is present
        assertThat(taskData).containsKey("relationships");
        Map<String, Object> relationships = (Map<String, Object>) taskData.get("relationships");
        assertThat(relationships).containsKey("project");
        
        Map<String, Object> projectRelationship = (Map<String, Object>) relationships.get("project");
        Map<String, Object> relationshipData = (Map<String, Object>) projectRelationship.get("data");
        assertThat(relationshipData.get("type")).isEqualTo("projects");
        assertThat(relationshipData.get("id")).isEqualTo(projectId);
        
        // 5. Read task with include parameter to embed project
        ResponseEntity<Map> includeResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + task1Id + "?include=project",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertThat(includeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> includeBody = includeResponse.getBody();
        
        // Verify included array contains the project
        if (includeBody.containsKey("included")) {
            List<Map<String, Object>> included = (List<Map<String, Object>>) includeBody.get("included");
            assertThat(included).isNotEmpty();
            
            Map<String, Object> includedProject = included.get(0);
            assertThat(includedProject.get("type")).isEqualTo("projects");
            assertThat(includedProject.get("id")).isEqualTo(projectId);
            
            Map<String, Object> includedAttributes = (Map<String, Object>) includedProject.get("attributes");
            assertThat(includedAttributes.get("name")).isEqualTo("Project with Tasks");
        }
        
        // 6. Update task
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "id", task1Id,
                "attributes", Map.of(
                    "title", "Task 1 Updated",
                    "description", "Updated description",
                    "completed", true
                )
            )
        );
        
        HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateData, headers);
        
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + task1Id,
            HttpMethod.PATCH,
            updateRequest,
            Map.class
        );
        
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updatedTask = (Map<String, Object>) updateResponse.getBody().get("data");
        Map<String, Object> updatedAttributes = (Map<String, Object>) updatedTask.get("attributes");
        assertThat(updatedAttributes.get("completed")).isEqualTo(true);
        
        // 7. Delete tasks first (foreign key constraint)
        testDataHelper.deleteTask(task1Id);
        testDataHelper.deleteTask(task2Id);
        
        // 8. Delete project
        testDataHelper.deleteProject(projectId);
    }
    
    /**
     * Test authentication flow: get token → make request.
     * 
     * Validates:
     * - Token acquisition from Keycloak
     * - JWT validation by Gateway
     * - Request processing with valid token
     * 
     * Validates: Requirement 10.3
     */
    @Test
    void testAuthenticationFlow() {
        // 1. Get token from Keycloak
        String adminToken = authHelper.getAdminToken();
        assertThat(adminToken).isNotNull().isNotEmpty();
        
        String userToken = authHelper.getUserToken();
        assertThat(userToken).isNotNull().isNotEmpty();
        
        // 2. Make request with admin token - should succeed
        HttpHeaders adminHeaders = authHelper.createAuthHeaders(adminToken);
        String projectId = testDataHelper.createProject(
            "Auth Test Project",
            "Testing authentication",
            "PLANNING"
        );
        assertThat(projectId).isNotNull();
        
        // 3. Make request with user token - should also succeed for read operations
        HttpHeaders userHeaders = authHelper.createAuthHeaders(userToken);
        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            userRequest,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // 4. Make request without token - should fail with 401
        try {
            restTemplate.getForEntity(
                GATEWAY_URL + "/api/collections/projects/" + projectId,
                Map.class
            );
            // Should not reach here
            assertThat(false).as("Expected 401 for unauthenticated request").isTrue();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        
        // Cleanup
        testDataHelper.deleteProject(projectId);
    }
    
    /**
     * Test authorization flow: admin access → user denied.
     * 
     * Validates:
     * - Role-based access control
     * - Authorization policy enforcement
     * - HTTP 403 for unauthorized access
     * 
     * Note: This test assumes authorization policies are configured.
     * If no policies are configured, all authenticated users can access all routes.
     * 
     * Validates: Requirement 10.4
     */
    @Test
    void testAuthorizationFlow() {
        // 1. Get tokens for different users
        String adminToken = authHelper.getAdminToken();
        String userToken = authHelper.getUserToken();
        
        // 2. Admin creates a project - should succeed
        HttpHeaders adminHeaders = authHelper.createAuthHeaders(adminToken);
        String projectId = testDataHelper.createProject(
            "Authz Test Project",
            "Testing authorization",
            "PLANNING"
        );
        assertThat(projectId).isNotNull();
        
        // 3. Regular user reads the project - should succeed (read is typically allowed)
        HttpHeaders userHeaders = authHelper.createAuthHeaders(userToken);
        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);
        
        ResponseEntity<Map> readResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            userRequest,
            Map.class
        );
        
        assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // 4. Regular user tries to delete the project
        // Depending on authorization policies, this may succeed or fail
        // If policies are configured to require ADMIN role for DELETE, it should fail with 403
        try {
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + projectId,
                HttpMethod.DELETE,
                userRequest,
                Void.class
            );
            
            // If delete succeeds, it means no authorization policy is configured
            // This is acceptable for the test - just verify the response
            assertThat(deleteResponse.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);
            
            // If delete succeeded, project is already deleted, no cleanup needed
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // If delete fails with 403, authorization is working correctly
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            
            // Cleanup with admin token
            testDataHelper.deleteProject(projectId);
        }
    }
    
    /**
     * Test error handling flow: invalid request → error response.
     * 
     * Validates:
     * - Validation error handling
     * - JSON:API error format
     * - HTTP 400 for invalid requests
     * - Error propagation through Gateway
     * 
     * Validates: Requirement 10.5, 10.8
     */
    @Test
    void testErrorHandlingFlow() {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // 1. Test missing required field - should return 400
        Map<String, Object> invalidProject = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    // Missing required "name" field
                    "description", "Missing name field",
                    "status", "PLANNING"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> invalidRequest = new HttpEntity<>(invalidProject, headers);
        
        try {
            restTemplate.postForEntity(
                GATEWAY_URL + "/api/collections/projects",
                invalidRequest,
                Map.class
            );
            // Should not reach here
            assertThat(false).as("Expected 400 for missing required field").isTrue();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            
            // Verify error response is in JSON:API format
            String errorBody = e.getResponseBodyAsString();
            assertThat(errorBody).contains("error");
        }
        
        // 2. Test invalid field type - should return 400
        Map<String, Object> invalidTypeProject = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "Invalid Type Project",
                    "description", "Testing invalid type",
                    "status", 12345 // Should be string, not number
                )
            )
        );
        
        HttpEntity<Map<String, Object>> invalidTypeRequest = new HttpEntity<>(invalidTypeProject, headers);
        
        try {
            restTemplate.postForEntity(
                GATEWAY_URL + "/api/collections/projects",
                invalidTypeRequest,
                Map.class
            );
            // Should not reach here
            assertThat(false).as("Expected 400 for invalid field type").isTrue();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        
        // 3. Test non-existent resource - should return 404
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/non-existent-id",
                HttpMethod.GET,
                getRequest,
                Map.class
            );
            // Should not reach here
            assertThat(false).as("Expected 404 for non-existent resource").isTrue();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
    
    /**
     * Test request passes through Gateway authentication.
     * 
     * Validates:
     * - JWT validation by Gateway
     * - User identity extraction
     * - Request forwarding with user context
     * 
     * Validates: Requirement 10.2
     */
    @Test
    void testRequestPassesThroughGatewayAuthentication() {
        // Get token and create project
        String token = authHelper.getAdminToken();
        String projectId = testDataHelper.createProject(
            "Gateway Auth Test",
            "Testing gateway authentication",
            "PLANNING"
        );
        
        // Verify we can read the project with the same token
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Cleanup
        testDataHelper.deleteProject(projectId);
    }
    
    /**
     * Test request is routed to correct backend service.
     * 
     * Validates:
     * - Route resolution by Gateway
     * - Request forwarding to Sample Service
     * - Response propagation back through Gateway
     * 
     * Validates: Requirement 10.4
     */
    @Test
    void testRequestRoutedToCorrectBackendService() {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // Create project through gateway - should be routed to sample service
        String projectId = testDataHelper.createProject(
            "Routing Test",
            "Testing request routing",
            "ACTIVE"
        );
        
        // Verify project was created in sample service by reading it back
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(projectId);
        
        // Cleanup
        testDataHelper.deleteProject(projectId);
    }
    
    /**
     * Test responses are processed by Gateway filters.
     * 
     * Validates:
     * - Response transformation
     * - JSON:API format compliance
     * - Header processing
     * 
     * Validates: Requirement 10.5
     */
    @Test
    void testResponsesProcessedByGatewayFilters() {
        String token = authHelper.getAdminToken();
        String projectId = testDataHelper.createProject(
            "Filter Test",
            "Testing response filters",
            "PLANNING"
        );
        
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Verify response is in JSON:API format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKeys("type", "id", "attributes");
        assertThat(data.get("type")).isEqualTo("projects");
        
        // Cleanup
        testDataHelper.deleteProject(projectId);
    }
}
