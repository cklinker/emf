package com.emf.gateway.integration;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class for managing test data in integration tests.
 * 
 * Provides methods to:
 * - Create test projects and tasks
 * - Delete test projects and tasks
 * - Track created resources for cleanup
 * 
 * Validates: Requirements 3.7, 3.8, 15.1, 15.2
 */
public class TestDataHelper {
    
    private static final String GATEWAY_URL = "http://localhost:8080";
    
    private final RestTemplate restTemplate;
    private final AuthenticationHelper authHelper;
    
    // Track created resources for cleanup
    private final List<String> createdProjectIds = new ArrayList<>();
    private final List<String> createdTaskIds = new ArrayList<>();
    
    public TestDataHelper(RestTemplate restTemplate, AuthenticationHelper authHelper) {
        this.restTemplate = restTemplate;
        this.authHelper = authHelper;
    }
    
    /**
     * Create a project via the gateway.
     * 
     * @param name The project name
     * @param description The project description
     * @param status The project status (PLANNING, ACTIVE, COMPLETED, ARCHIVED)
     * @return The ID of the created project
     */
    public String createProject(String name, String description, String status) {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", name,
                    "description", description,
                    "status", status
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectData, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/projects",
            request,
            Map.class
        );
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        String projectId = (String) data.get("id");
        
        // Track for cleanup
        createdProjectIds.add(projectId);
        
        return projectId;
    }
    
    /**
     * Create a task via the gateway.
     * 
     * @param title The task title
     * @param description The task description
     * @param projectId The ID of the project this task belongs to
     * @return The ID of the created task
     */
    public String createTask(String title, String description, String projectId) {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> taskData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "attributes", Map.of(
                    "title", title,
                    "description", description,
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
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/tasks",
            request,
            Map.class
        );
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        String taskId = (String) data.get("id");
        
        // Track for cleanup
        createdTaskIds.add(taskId);
        
        return taskId;
    }
    
    /**
     * Delete a project via the gateway.
     * 
     * @param projectId The ID of the project to delete
     */
    public void deleteProject(String projectId) {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        // Remove from tracking
        createdProjectIds.remove(projectId);
    }
    
    /**
     * Delete a task via the gateway.
     * 
     * @param taskId The ID of the task to delete
     */
    public void deleteTask(String taskId) {
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + taskId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        // Remove from tracking
        createdTaskIds.remove(taskId);
    }
    
    /**
     * Clean up all tracked test data.
     * Deletes all tasks first (to avoid foreign key constraints), then projects.
     */
    public void cleanupAll() {
        // Delete tasks first to avoid foreign key constraint violations
        List<String> tasksCopy = new ArrayList<>(createdTaskIds);
        for (String taskId : tasksCopy) {
            try {
                deleteTask(taskId);
            } catch (Exception e) {
                // Ignore errors during cleanup
                // Resource may have already been deleted
            }
        }
        
        // Then delete projects
        List<String> projectsCopy = new ArrayList<>(createdProjectIds);
        for (String projectId : projectsCopy) {
            try {
                deleteProject(projectId);
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
     * Get the list of created project IDs.
     * 
     * @return List of project IDs created by this helper
     */
    public List<String> getCreatedProjectIds() {
        return new ArrayList<>(createdProjectIds);
    }
    
    /**
     * Get the list of created task IDs.
     * 
     * @return List of task IDs created by this helper
     */
    public List<String> getCreatedTaskIds() {
        return new ArrayList<>(createdTaskIds);
    }
}
