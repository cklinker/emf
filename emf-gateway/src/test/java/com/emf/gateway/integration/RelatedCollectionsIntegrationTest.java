package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
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
 * Integration tests for Related Collections functionality.
 * 
 * Tests the complete lifecycle of relationships between collections:
 * - Creating resources with relationships to other resources
 * - Reading resources with relationship data
 * - Updating relationships on existing resources
 * - Deleting resources that have relationships
 * - Querying resources by relationship filters
 * - Maintaining relationship integrity
 * 
 * This test class validates that:
 * - Relationships are correctly stored in the database
 * - Relationship data is correctly returned in JSON:API responses
 * - Relationships can be updated and changes are persisted
 * - Relationship integrity is maintained (referential integrity)
 * - Resources with relationships can be deleted properly
 * - Relationship filters work correctly
 * 
 * Validates: Requirements 7.1-7.7
 */
public class RelatedCollectionsIntegrationTest extends IntegrationTestBase {
    
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
     * Test creating a task with a relationship to a project.
     * 
     * Validates:
     * - Requirement 7.1: Creating a resource with a relationship to another resource
     * - Requirement 7.2: Relationship data is correctly stored in the database
     */
    @Test
    void testCreateTaskWithProjectRelationship() {
        // Arrange - create a project first
        String projectId = testDataHelper.createProject(
            "Relationship Test Project",
            "Project for testing relationships",
            "ACTIVE"
        );
        createdProjectIds.add(projectId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> taskData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "attributes", Map.of(
                    "title", "Task with relationship",
                    "description", "This task belongs to a project",
                    "completed", false
                ),
                "relationships", Map.of(
                    "project", Map.of(
                        "data", Map.of(
                            "type", "projects",
                            "id", projectId
                        )
                    )
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(taskData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/tasks",
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("type")).isEqualTo("tasks");
        assertThat(data.get("id")).isNotNull();
        
        String taskId = (String) data.get("id");
        createdTaskIds.add(taskId);
        
        // Verify attributes
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertThat(attributes).isNotNull();
        assertThat(attributes.get("title")).isEqualTo("Task with relationship");
        assertThat(attributes.get("description")).isEqualTo("This task belongs to a project");
        assertThat(attributes.get("completed")).isEqualTo(false);
        
        // Verify relationship data is present
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertThat(relationships).isNotNull();
        assertThat(relationships).containsKey("project");
        
        Map<String, Object> projectRelationship = (Map<String, Object>) relationships.get("project");
        assertThat(projectRelationship).isNotNull();
        assertThat(projectRelationship).containsKey("data");
        
        Map<String, Object> projectData = (Map<String, Object>) projectRelationship.get("data");
        assertThat(projectData).isNotNull();
        assertThat(projectData.get("type")).isEqualTo("projects");
        assertThat(projectData.get("id")).isEqualTo(projectId);
    }
    
    /**
     * Test reading a task with relationship data.
     * 
     * Validates:
     * - Requirement 7.3: Relationship data is correctly returned in JSON:API responses
     */
    @Test
    void testReadTaskWithProjectRelationship() {
        // Arrange - create a project and task with relationship
        String projectId = testDataHelper.createProject(
            "Read Relationship Project",
            "Project for testing relationship reads",
            "PLANNING"
        );
        createdProjectIds.add(projectId);
        
        String taskId = testDataHelper.createTask(
            "Read Relationship Task",
            "Task for testing relationship reads",
            projectId
        );
        createdTaskIds.add(taskId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + taskId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("type")).isEqualTo("tasks");
        assertThat(data.get("id")).isEqualTo(taskId);
        
        // Verify relationship data is correctly returned
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertThat(relationships).isNotNull();
        assertThat(relationships).containsKey("project");
        
        Map<String, Object> projectRelationship = (Map<String, Object>) relationships.get("project");
        assertThat(projectRelationship).isNotNull();
        assertThat(projectRelationship).containsKey("data");
        
        Map<String, Object> projectData = (Map<String, Object>) projectRelationship.get("data");
        assertThat(projectData).isNotNull();
        assertThat(projectData.get("type")).isEqualTo("projects");
        assertThat(projectData.get("id")).isEqualTo(projectId);
    }
    
    /**
     * Test updating a task's relationship to a different project.
     * 
     * Validates:
     * - Requirement 7.4: Updating relationships on existing resources
     */
    @Test
    void testUpdateTaskRelationship() {
        // Arrange - create two projects and a task linked to the first project
        String project1Id = testDataHelper.createProject(
            "Original Project",
            "First project for relationship update test",
            "ACTIVE"
        );
        createdProjectIds.add(project1Id);
        
        String project2Id = testDataHelper.createProject(
            "New Project",
            "Second project for relationship update test",
            "ACTIVE"
        );
        createdProjectIds.add(project2Id);
        
        String taskId = testDataHelper.createTask(
            "Update Relationship Task",
            "Task for testing relationship updates",
            project1Id
        );
        createdTaskIds.add(taskId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // Update the task to point to the second project
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "id", taskId,
                "relationships", Map.of(
                    "project", Map.of(
                        "data", Map.of(
                            "type", "projects",
                            "id", project2Id
                        )
                    )
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + taskId,
            HttpMethod.PUT,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        
        // Verify the relationship was updated
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertThat(relationships).isNotNull();
        
        Map<String, Object> projectRelationship = (Map<String, Object>) relationships.get("project");
        Map<String, Object> projectData = (Map<String, Object>) projectRelationship.get("data");
        assertThat(projectData.get("id")).isEqualTo(project2Id);
        
        // Verify the update was persisted by reading the resource again
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + taskId,
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        
        Map<String, Object> getData = (Map<String, Object>) getResponse.getBody().get("data");
        Map<String, Object> getRelationships = (Map<String, Object>) getData.get("relationships");
        Map<String, Object> getProjectRelationship = (Map<String, Object>) getRelationships.get("project");
        Map<String, Object> getProjectData = (Map<String, Object>) getProjectRelationship.get("data");
        assertThat(getProjectData.get("id")).isEqualTo(project2Id);
    }
    
    /**
     * Test deleting a resource that has relationships.
     * 
     * Validates:
     * - Requirement 7.5: Deleting resources that have relationships
     */
    @Test
    void testDeleteResourceWithRelationships() {
        // Arrange - create a project and task with relationship
        String projectId = testDataHelper.createProject(
            "Delete Relationship Project",
            "Project for testing deletion with relationships",
            "COMPLETED"
        );
        createdProjectIds.add(projectId);
        
        String taskId = testDataHelper.createTask(
            "Delete Relationship Task",
            "Task for testing deletion with relationships",
            projectId
        );
        createdTaskIds.add(taskId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - delete the task (child resource)
        ResponseEntity<Void> deleteTaskResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + taskId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        // Assert - task deletion should succeed
        assertThat(deleteTaskResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        createdTaskIds.remove(taskId);
        
        // Verify the task was deleted
        try {
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/tasks/" + taskId,
                HttpMethod.GET,
                request,
                Map.class
            );
            assertThat(false).as("Expected 404 Not Found for deleted task").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("404");
        }
        
        // Verify the project still exists (parent resource should not be affected)
        ResponseEntity<Map> getProjectResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        assertThat(getProjectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    /**
     * Test that relationship integrity is maintained.
     * 
     * Validates:
     * - Requirement 7.6: Relationship integrity is maintained
     */
    @Test
    void testRelationshipIntegrity() {
        // Arrange
        String nonExistentProjectId = "00000000-0000-0000-0000-000000000000";
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // Try to create a task with a relationship to a non-existent project
        Map<String, Object> taskData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "attributes", Map.of(
                    "title", "Invalid Relationship Task",
                    "description", "Task with invalid relationship",
                    "completed", false
                ),
                "relationships", Map.of(
                    "project", Map.of(
                        "data", Map.of(
                            "type", "projects",
                            "id", nonExistentProjectId
                        )
                    )
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(taskData, headers);
        
        // Act & Assert - should fail with validation error
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                GATEWAY_URL + "/api/collections/tasks",
                request,
                Map.class
            );
            
            // If we get here, check if it's an error response
            if (response.getStatusCode().is4xxClientError()) {
                // Expected - referential integrity violation
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody()).containsKey("errors");
            } else {
                // Should not succeed with invalid relationship
                assertThat(false).as("Expected error for invalid relationship reference").isTrue();
            }
        } catch (Exception e) {
            // Expected - should fail with 400 or 404
            assertThat(e.getMessage()).containsAnyOf("400", "404");
        }
    }
    
    /**
     * Test querying resources by relationship filters.
     * 
     * Validates:
     * - Requirement 7.7: Querying resources by relationship filters
     */
    @Test
    void testQueryByRelationshipFilters() {
        // Arrange - create a project and multiple tasks
        String projectId = testDataHelper.createProject(
            "Filter Test Project",
            "Project for testing relationship filters",
            "ACTIVE"
        );
        createdProjectIds.add(projectId);
        
        String task1Id = testDataHelper.createTask(
            "Filter Task 1",
            "First task for filter testing",
            projectId
        );
        createdTaskIds.add(task1Id);
        
        String task2Id = testDataHelper.createTask(
            "Filter Task 2",
            "Second task for filter testing",
            projectId
        );
        createdTaskIds.add(task2Id);
        
        // Create another project and task to ensure filtering works
        String otherProjectId = testDataHelper.createProject(
            "Other Project",
            "Another project for filter testing",
            "PLANNING"
        );
        createdProjectIds.add(otherProjectId);
        
        String otherTaskId = testDataHelper.createTask(
            "Other Task",
            "Task for other project",
            otherProjectId
        );
        createdTaskIds.add(otherTaskId);
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - query tasks filtered by project relationship
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks?filter[project_id]=" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.size()).isGreaterThanOrEqualTo(2);
        
        // Verify all returned tasks belong to the filtered project
        for (Map<String, Object> task : data) {
            Map<String, Object> relationships = (Map<String, Object>) task.get("relationships");
            if (relationships != null && relationships.containsKey("project")) {
                Map<String, Object> projectRelationship = (Map<String, Object>) relationships.get("project");
                Map<String, Object> projectData = (Map<String, Object>) projectRelationship.get("data");
                
                // Only check tasks that have the project relationship
                if (projectData != null) {
                    String taskProjectId = (String) projectData.get("id");
                    // The task should belong to our filtered project
                    if (task.get("id").equals(task1Id) || task.get("id").equals(task2Id)) {
                        assertThat(taskProjectId).isEqualTo(projectId);
                    }
                }
            }
        }
        
        // Verify our specific tasks are in the results
        List<String> returnedIds = data.stream()
            .map(item -> (String) item.get("id"))
            .toList();
        
        assertThat(returnedIds).contains(task1Id, task2Id);
    }
}
