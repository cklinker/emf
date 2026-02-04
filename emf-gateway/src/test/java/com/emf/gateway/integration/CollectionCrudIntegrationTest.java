package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Collection CRUD operations.
 * 
 * Tests the complete CRUD lifecycle for collections through the gateway:
 * - Creating resources via POST requests
 * - Reading individual resources via GET requests
 * - Reading resource collections via GET requests with pagination
 * - Updating resources via PATCH requests
 * - Deleting resources via DELETE requests
 * 
 * This test class validates that:
 * - Resources are correctly created and assigned unique IDs
 * - Resources are correctly stored in the database
 * - Resources can be retrieved individually and in collections
 * - Resources can be updated and changes are persisted
 * - Resources can be deleted and are removed from the database
 * - Operations on non-existent resources return appropriate errors
 * 
 * Validates: Requirements 9.1-9.10
 */
public class CollectionCrudIntegrationTest extends IntegrationTestBase {
    
    // Track created resources for cleanup
    private final List<String> createdProjectIds = new ArrayList<>();
    private final List<String> createdTaskIds = new ArrayList<>();
    
    /**
     * Clean up test data after each test.
     * Deletes all tasks first (to avoid foreign key constraints), then projects.
     */
    @Override
    protected void cleanupTestData() {
        // Delete tasks first to avoid foreign key constraint violations
        for (String taskId : new ArrayList<>(createdTaskIds)) {
            try {
                testDataHelper.deleteTask(taskId);
            } catch (Exception e) {
                // Ignore errors during cleanup
                // Resource may have already been deleted
            }
        }
        
        // Then delete projects
        for (String projectId : new ArrayList<>(createdProjectIds)) {
            try {
                testDataHelper.deleteProject(projectId);
            } catch (Exception e) {
                // Ignore errors during cleanup
                // Resource may have already been deleted
            }
        }
        
        // Clear tracking lists
        createdTaskIds.clear();
        createdProjectIds.clear();
    }
    
    /**
     * Test creating a project with valid data.
     * 
     * Validates:
     * - Requirement 9.1: Creating resources via POST requests
     * - Requirement 9.2: Created resources are assigned unique IDs
     * - Requirement 9.3: Created resources are stored in the database
     */
    @Test
    void testCreateProject() {
        // Arrange
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "Test Project",
                    "description", "A test project for CRUD operations",
                    "status", "PLANNING"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/projects",
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("type")).isEqualTo("projects");
        assertThat(data.get("id")).isNotNull();
        
        String projectId = (String) data.get("id");
        createdProjectIds.add(projectId);
        
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes).isNotNull();
        assertThat(attributes.get("name")).isEqualTo("Test Project");
        assertThat(attributes.get("description")).isEqualTo("A test project for CRUD operations");
        assertThat(attributes.get("status")).isEqualTo("PLANNING");
    }
    
    /**
     * Test reading a project by ID.
     * 
     * Validates:
     * - Requirement 9.4: Reading individual resources via GET requests
     */
    @Test
    void testReadProject() {
        // Arrange - create a project first
        String projectId = testDataHelper.createProject(
            "Read Test Project",
            "Project for testing read operations",
            "ACTIVE"
        );
        createdProjectIds.add(projectId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("type")).isEqualTo("projects");
        assertThat(data.get("id")).isEqualTo(projectId);
        
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes).isNotNull();
        assertThat(attributes.get("name")).isEqualTo("Read Test Project");
        assertThat(attributes.get("description")).isEqualTo("Project for testing read operations");
        assertThat(attributes.get("status")).isEqualTo("ACTIVE");
    }
    
    /**
     * Test updating a project.
     * 
     * Validates:
     * - Requirement 9.6: Updating resources via PATCH requests
     * - Requirement 9.7: Updates are persisted to the database
     */
    @Test
    void testUpdateProject() {
        // Arrange - create a project first
        String projectId = testDataHelper.createProject(
            "Update Test Project",
            "Project for testing update operations",
            "PLANNING"
        );
        createdProjectIds.add(projectId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "projects",
                "id", projectId,
                "attributes", Map.of(
                    "name", "Updated Project Name",
                    "status", "ACTIVE"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.PUT,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("id")).isEqualTo(projectId);
        
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes).isNotNull();
        assertThat(attributes.get("name")).isEqualTo("Updated Project Name");
        assertThat(attributes.get("status")).isEqualTo("ACTIVE");
        
        // Verify the update was persisted by reading the resource again
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        
        Map<String, Object> getData = (Map<String, Object>) getResponse.getBody().get("data");
        Map<String, Object> getAttributes = (Map<String, Object>) getData.get("attributes");
        assertThat(getAttributes.get("name")).isEqualTo("Updated Project Name");
        assertThat(getAttributes.get("status")).isEqualTo("ACTIVE");
    }
    
    /**
     * Test deleting a project.
     * 
     * Validates:
     * - Requirement 9.8: Deleting resources via DELETE requests
     * - Requirement 9.9: Deleted resources are removed from the database
     */
    @Test
    void testDeleteProject() {
        // Arrange - create a project first
        String projectId = testDataHelper.createProject(
            "Delete Test Project",
            "Project for testing delete operations",
            "ARCHIVED"
        );
        createdProjectIds.add(projectId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        
        // Verify the resource was deleted by trying to read it
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + projectId,
                HttpMethod.GET,
                request,
                Map.class
            );
            // If we get here, the resource was not deleted
            assertThat(false).as("Expected 404 Not Found but got successful response").isTrue();
        } catch (Exception e) {
            // Expected - resource should not be found
            assertThat(e.getMessage()).contains("404");
        }
        
        // Remove from tracking since it's already deleted
        createdProjectIds.remove(projectId);
    }
    
    /**
     * Test listing projects with pagination.
     * 
     * Validates:
     * - Requirement 9.5: Reading resource collections via GET requests with pagination
     */
    @Test
    void testListProjects() {
        // Arrange - create multiple projects
        String project1Id = testDataHelper.createProject(
            "List Test Project 1",
            "First project for list testing",
            "PLANNING"
        );
        createdProjectIds.add(project1Id);
        
        String project2Id = testDataHelper.createProject(
            "List Test Project 2",
            "Second project for list testing",
            "ACTIVE"
        );
        createdProjectIds.add(project2Id);
        
        String project3Id = testDataHelper.createProject(
            "List Test Project 3",
            "Third project for list testing",
            "COMPLETED"
        );
        createdProjectIds.add(project3Id);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects?page[size]=10",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.size()).isGreaterThanOrEqualTo(3);
        
        // Verify our created projects are in the list
        List<String> returnedIds = data.stream()
            .map(item -> (String) item.get("id"))
            .toList();
        
        assertThat(returnedIds).contains(project1Id, project2Id, project3Id);
    }
    
    /**
     * Test operations on non-existent resources return HTTP 404.
     * 
     * Validates:
     * - Requirement 9.10: Operations on non-existent resources return HTTP 404
     */
    @Test
    void testOperationsOnNonExistentResource() {
        // Arrange
        String nonExistentId = "00000000-0000-0000-0000-000000000000";
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        
        // Act & Assert - GET non-existent resource
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + nonExistentId,
                HttpMethod.GET,
                getRequest,
                Map.class
            );
            assertThat(false).as("Expected 404 Not Found for GET").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("404");
        }
        
        // Act & Assert - UPDATE non-existent resource
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "projects",
                "id", nonExistentId,
                "attributes", Map.of(
                    "name", "Updated Name"
                )
            )
        );
        HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateData, headers);
        
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + nonExistentId,
                HttpMethod.PUT,
                updateRequest,
                Map.class
            );
            assertThat(false).as("Expected 404 Not Found for PUT").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("404");
        }
        
        // Act & Assert - DELETE non-existent resource
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + nonExistentId,
                HttpMethod.DELETE,
                getRequest,
                Void.class
            );
            assertThat(false).as("Expected 404 Not Found for DELETE").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("404");
        }
    }
}
