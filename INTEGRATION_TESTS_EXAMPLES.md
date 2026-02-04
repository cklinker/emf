# Integration Test Examples

This document provides example test cases for each test category, demonstrating best practices and common patterns.

## Table of Contents

- [Infrastructure Tests](#infrastructure-tests)
- [Authentication Tests](#authentication-tests)
- [Authorization Tests](#authorization-tests)
- [CRUD Operation Tests](#crud-operation-tests)
- [Relationship Tests](#relationship-tests)
- [Include Parameter Tests](#include-parameter-tests)
- [Cache Integration Tests](#cache-integration-tests)
- [Event-Driven Configuration Tests](#event-driven-configuration-tests)
- [Error Handling Tests](#error-handling-tests)
- [End-to-End Tests](#end-to-end-tests)
- [Property-Based Tests](#property-based-tests)

## Infrastructure Tests

### Example: Health Check Test

Tests that all services are healthy and accessible.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests infrastructure service health checks.
 * 
 * Purpose: Verify all services start correctly and are accessible
 * Category: Infrastructure
 */
public class HealthCheckIntegrationTest extends IntegrationTestBase {
    
    @Test
    void testGatewayHealth() {
        // Arrange - Gateway should be running
        
        // Act - Check health endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
            GATEWAY_URL + "/actuator/health",
            String.class
        );
        
        // Assert - Gateway is healthy
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }
    
    @Test
    void testControlPlaneHealth() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
            CONTROL_PLANE_URL + "/actuator/health",
            String.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }
    
    @Test
    void testSampleServiceHealth() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
            SAMPLE_SERVICE_URL + "/actuator/health",
            String.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }
}
```


## Authentication Tests

### Example: Token Acquisition and Validation

Tests JWT token acquisition from Keycloak and validation by the Gateway.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authentication flows with Keycloak and JWT validation.
 * 
 * Purpose: Verify JWT token acquisition and validation
 * Category: Authentication
 */
public class AuthenticationExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Test
    void testGetAdminToken() {
        // Act - Get token for admin user
        String token = authHelper.getAdminToken();
        
        // Assert - Token is not null and has JWT format
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "Token should have 3 parts");
    }
    
    @Test
    void testRequestWithoutToken_Returns401() {
        // Arrange - No authorization header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - Make request without token
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert - Returns 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
    
    @Test
    void testRequestWithInvalidToken_Returns401() {
        // Arrange - Invalid token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.token.here");
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
    
    @Test
    void testRequestWithValidToken_Succeeds() {
        // Arrange - Valid token
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert - Request succeeds (200 or 404, but not 401)
        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
```

## Authorization Tests

### Example: Role-Based Access Control

Tests that authorization policies are enforced based on user roles.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authorization policy enforcement.
 * 
 * Purpose: Verify role-based access control works correctly
 * Category: Authorization
 */
public class AuthorizationExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Test
    void testAdminCanAccessAdminRoute() {
        // Arrange - Admin token
        String adminToken = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - Access admin-only route (control plane)
        ResponseEntity<Map> response = restTemplate.exchange(
            CONTROL_PLANE_URL + "/control/collections",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert - Admin can access
        assertTrue(
            response.getStatusCode() == HttpStatus.OK || 
            response.getStatusCode() == HttpStatus.NOT_FOUND,
            "Admin should be able to access admin routes"
        );
    }
    
    @Test
    void testUserCannotAccessAdminRoute() {
        // Arrange - Regular user token
        String userToken = authHelper.getUserToken();
        HttpHeaders headers = authHelper.createAuthHeaders(userToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - Try to access admin-only route
        ResponseEntity<Map> response = restTemplate.exchange(
            CONTROL_PLANE_URL + "/control/collections",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert - User is denied access
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
    
    @Test
    void testFieldFilteringBasedOnRole() {
        // Arrange - Create a project with admin
        String adminToken = authHelper.getAdminToken();
        String projectId = testDataHelper.createProject(
            "Test Project",
            "Sensitive description",
            "ACTIVE"
        );
        
        // Act - Get project as regular user
        String userToken = authHelper.getUserToken();
        HttpHeaders headers = authHelper.createAuthHeaders(userToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert - Sensitive fields are filtered
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        
        // Verify field filtering (if configured)
        assertNotNull(attributes.get("name"));
        // If description is admin-only, it should be filtered for regular users
    }
}
```

## CRUD Operation Tests

### Example: Complete CRUD Lifecycle

Tests create, read, update, and delete operations on a collection.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CRUD operations on collections.
 * 
 * Purpose: Verify create, read, update, delete operations work correctly
 * Category: CRUD
 */
public class CrudOperationsExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    private String createdProjectId;
    
    @Test
    void testCreateProject() {
        // Arrange
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "New Project",
                    "description", "Test project",
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
        
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertEquals("New Project", attributes.get("name"));
        assertEquals("Test project", attributes.get("description"));
        assertEquals("PLANNING", attributes.get("status"));
        
        // Save ID for cleanup
        createdProjectId = (String) data.get("id");
    }
    
    @Test
    void testReadProject() {
        // Arrange - Create a project first
        String token = authHelper.getAdminToken();
        String projectId = createTestProject(token);
        
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
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(projectId, data.get("id"));
        assertEquals("projects", data.get("type"));
    }
    
    @Test
    void testUpdateProject() {
        // Arrange - Create a project
        String token = authHelper.getAdminToken();
        String projectId = createTestProject(token);
        
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "projects",
                "id", projectId,
                "attributes", Map.of(
                    "status", "ACTIVE"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.PATCH,
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertEquals("ACTIVE", attributes.get("status"));
    }
    
    @Test
    void testDeleteProject() {
        // Arrange - Create a project
        String token = authHelper.getAdminToken();
        String projectId = createTestProject(token);
        
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
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        
        // Verify project is deleted
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
    }
    
    @Override
    protected void cleanupTestData() {
        if (createdProjectId != null) {
            try {
                String token = authHelper.getAdminToken();
                HttpHeaders headers = authHelper.createAuthHeaders(token);
                HttpEntity<Void> request = new HttpEntity<>(headers);
                
                restTemplate.exchange(
                    GATEWAY_URL + "/api/collections/projects/" + createdProjectId,
                    HttpMethod.DELETE,
                    request,
                    Void.class
                );
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    private String createTestProject(String token) {
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "Test Project",
                    "description", "For testing",
                    "status", "PLANNING"
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
        return (String) data.get("id");
    }
}
```


## Relationship Tests

### Example: Creating and Reading Related Resources

Tests creating resources with relationships and reading them back.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests relationship handling between collections.
 * 
 * Purpose: Verify relationships are created, stored, and retrieved correctly
 * Category: Relationships
 */
public class RelationshipExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Test
    void testCreateTaskWithProjectRelationship() {
        // Arrange - Create a project first
        String projectId = testDataHelper.createProject(
            "Parent Project",
            "Project for tasks",
            "ACTIVE"
        );
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> taskData = Map.of(
            "data", Map.of(
                "type", "tasks",
                "attributes", Map.of(
                    "title", "New Task",
                    "description", "Task description",
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertNotNull(relationships);
        
        Map<String, Object> project = (Map<String, Object>) relationships.get("project");
        Map<String, Object> projectData = (Map<String, Object>) project.get("data");
        assertEquals(projectId, projectData.get("id"));
        assertEquals("projects", projectData.get("type"));
    }
    
    @Test
    void testReadTaskWithRelationship() {
        // Arrange - Create project and task
        String projectId = testDataHelper.createProject(
            "Test Project",
            "Description",
            "ACTIVE"
        );
        
        String taskId = testDataHelper.createTask(
            "Test Task",
            "Task description",
            projectId
        );
        
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
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
        assertNotNull(relationships);
        
        Map<String, Object> project = (Map<String, Object>) relationships.get("project");
        Map<String, Object> projectData = (Map<String, Object>) project.get("data");
        assertEquals(projectId, projectData.get("id"));
    }
}
```

## Include Parameter Tests

### Example: Including Related Resources

Tests JSON:API include parameter for embedding related resources.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JSON:API include parameter processing.
 * 
 * Purpose: Verify related resources are embedded correctly
 * Category: Include Parameter
 */
public class IncludeParameterExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Test
    void testIncludeSingleRelationship() {
        // Arrange - Create project with tasks
        String projectId = testDataHelper.createProject(
            "Project with Tasks",
            "Description",
            "ACTIVE"
        );
        
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
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - Get project with included tasks
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId + "?include=tasks",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Verify primary data
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(projectId, data.get("id"));
        
        // Verify included array
        List<Map<String, Object>> included = 
            (List<Map<String, Object>>) response.getBody().get("included");
        assertNotNull(included);
        assertTrue(included.size() >= 2, "Should include at least 2 tasks");
        
        // Verify included tasks
        boolean foundTask1 = included.stream()
            .anyMatch(item -> task1Id.equals(item.get("id")));
        boolean foundTask2 = included.stream()
            .anyMatch(item -> task2Id.equals(item.get("id")));
        
        assertTrue(foundTask1, "Should include task 1");
        assertTrue(foundTask2, "Should include task 2");
    }
    
    @Test
    void testIncludeWithoutRelatedResources() {
        // Arrange - Create project without tasks
        String projectId = testDataHelper.createProject(
            "Empty Project",
            "No tasks",
            "PLANNING"
        );
        
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId + "?include=tasks",
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Included array should be empty or not present
        List<Map<String, Object>> included = 
            (List<Map<String, Object>>) response.getBody().get("included");
        
        if (included != null) {
            assertTrue(included.isEmpty(), "Should have no included resources");
        }
    }
}
```

## Cache Integration Tests

### Example: Redis Caching Behavior

Tests that resources are cached and retrieved from Redis.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Redis caching integration.
 * 
 * Purpose: Verify resources are cached correctly in Redis
 * Category: Cache
 */
public class CacheExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Test
    void testResourceIsCachedAfterCreation() {
        // Arrange
        String projectId = testDataHelper.createProject(
            "Cached Project",
            "Should be in Redis",
            "ACTIVE"
        );
        
        // Act - Check if resource is in Redis
        String cacheKey = "jsonapi:projects:" + projectId;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        
        // Assert
        assertNotNull(cachedValue, "Resource should be cached in Redis");
        assertTrue(cachedValue.contains("Cached Project"));
    }
    
    @Test
    void testCacheInvalidationOnDelete() {
        // Arrange - Create and verify cached
        String projectId = testDataHelper.createProject(
            "To Delete",
            "Will be deleted",
            "PLANNING"
        );
        
        String cacheKey = "jsonapi:projects:" + projectId;
        assertNotNull(redisTemplate.opsForValue().get(cacheKey));
        
        // Act - Delete project
        testDataHelper.deleteProject(projectId);
        
        // Wait for cache invalidation
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        // Assert - Cache should be invalidated
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        assertNull(cachedValue, "Cache should be invalidated after delete");
    }
}
```

## Event-Driven Configuration Tests

### Example: Kafka Event Processing

Tests that configuration changes are published to Kafka and processed by the Gateway.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

/**
 * Tests event-driven configuration updates via Kafka.
 * 
 * Purpose: Verify configuration changes propagate through Kafka
 * Category: Events
 */
public class EventDrivenConfigExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Test
    void testCollectionChangeEventPropagation() {
        // Arrange - Create a new collection via control plane
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> collectionData = Map.of(
            "name", "test-collection",
            "serviceId", "sample-service",
            "basePath", "/api/collections/test-collection",
            "fields", List.of(
                Map.of("name", "title", "type", "STRING", "required", true)
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(collectionData, headers);
        
        // Act - Create collection
        ResponseEntity<Map> response = restTemplate.postForEntity(
            CONTROL_PLANE_URL + "/control/collections",
            request,
            Map.class
        );
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Wait for event to propagate to gateway
        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                // Try to access the new collection route
                try {
                    ResponseEntity<Map> testResponse = restTemplate.exchange(
                        GATEWAY_URL + "/api/collections/test-collection",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                    );
                    // Route exists if we get 200 or 404, but not 503
                    return testResponse.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE;
                } catch (Exception e) {
                    return false;
                }
            });
        
        // Assert - Gateway now knows about the collection
        ResponseEntity<Map> finalResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/test-collection",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        assertNotEquals(HttpStatus.SERVICE_UNAVAILABLE, finalResponse.getStatusCode());
    }
}
```


## Error Handling Tests

### Example: Validation and Error Responses

Tests that validation errors are returned in JSON:API format.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests error handling and validation.
 * 
 * Purpose: Verify errors are handled correctly and returned in JSON:API format
 * Category: Error Handling
 */
public class ErrorHandlingExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Test
    void testMissingRequiredField_Returns400() {
        // Arrange - Project without required 'name' field
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> invalidData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "description", "Missing name field",
                    "status", "PLANNING"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(invalidData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/projects",
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Verify JSON:API error format
        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("errors"));
        
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertFalse(errors.isEmpty());
        
        Map<String, Object> error = errors.get(0);
        assertEquals("400", error.get("status"));
        assertNotNull(error.get("title"));
        assertNotNull(error.get("detail"));
    }
    
    @Test
    void testInvalidFieldType_Returns400() {
        // Arrange - Invalid status value
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> invalidData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "Test Project",
                    "status", "INVALID_STATUS"  // Not a valid enum value
                )
            )
        );
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(invalidData, headers);
        
        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/projects",
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertFalse(errors.isEmpty());
    }
    
    @Test
    void testResourceNotFound_Returns404() {
        // Arrange
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        String nonExistentId = "00000000-0000-0000-0000-000000000000";
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + nonExistentId,
            HttpMethod.GET,
            request,
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertFalse(errors.isEmpty());
        
        Map<String, Object> error = errors.get(0);
        assertEquals("404", error.get("status"));
    }
}
```

## End-to-End Tests

### Example: Complete Request Flow

Tests a complete workflow from authentication through CRUD operations.

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests complete end-to-end workflows.
 * 
 * Purpose: Verify complete request flows work correctly
 * Category: End-to-End
 */
public class EndToEndExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Test
    void testCompleteProjectLifecycle() {
        // Step 1: Authenticate
        String token = authHelper.getAdminToken();
        assertNotNull(token);
        
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        // Step 2: Create project
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", "E2E Test Project",
                    "description", "End-to-end test",
                    "status", "PLANNING"
                )
            )
        );
        
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(projectData, headers);
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/projects",
            createRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        
        Map<String, Object> createdData = (Map<String, Object>) createResponse.getBody().get("data");
        String projectId = (String) createdData.get("id");
        assertNotNull(projectId);
        
        // Step 3: Create tasks for project
        Map<String, Object> task1Data = Map.of(
            "data", Map.of(
                "type", "tasks",
                "attributes", Map.of(
                    "title", "Task 1",
                    "description", "First task",
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
        
        HttpEntity<Map<String, Object>> task1Request = new HttpEntity<>(task1Data, headers);
        ResponseEntity<Map> task1Response = restTemplate.postForEntity(
            GATEWAY_URL + "/api/collections/tasks",
            task1Request,
            Map.class
        );
        
        assertEquals(HttpStatus.CREATED, task1Response.getStatusCode());
        
        Map<String, Object> task1CreatedData = (Map<String, Object>) task1Response.getBody().get("data");
        String task1Id = (String) task1CreatedData.get("id");
        
        // Step 4: Read project with included tasks
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId + "?include=tasks",
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        
        List<Map<String, Object>> included = 
            (List<Map<String, Object>>) getResponse.getBody().get("included");
        assertNotNull(included);
        assertFalse(included.isEmpty());
        
        // Step 5: Update project status
        Map<String, Object> updateData = Map.of(
            "data", Map.of(
                "type", "projects",
                "id", projectId,
                "attributes", Map.of(
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
        
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        
        Map<String, Object> updatedData = (Map<String, Object>) updateResponse.getBody().get("data");
        Map<String, Object> updatedAttributes = (Map<String, Object>) updatedData.get("attributes");
        assertEquals("ACTIVE", updatedAttributes.get("status"));
        
        // Step 6: Delete task
        ResponseEntity<Void> deleteTaskResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/tasks/" + task1Id,
            HttpMethod.DELETE,
            getRequest,
            Void.class
        );
        
        assertEquals(HttpStatus.NO_CONTENT, deleteTaskResponse.getStatusCode());
        
        // Step 7: Delete project
        ResponseEntity<Void> deleteProjectResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.DELETE,
            getRequest,
            Void.class
        );
        
        assertEquals(HttpStatus.NO_CONTENT, deleteProjectResponse.getStatusCode());
        
        // Step 8: Verify project is deleted
        ResponseEntity<Map> verifyResponse = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            getRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.NOT_FOUND, verifyResponse.getStatusCode());
    }
}
```

## Property-Based Tests

### Example: Universal Properties with jqwik

Tests universal properties that should hold for all valid inputs.

```java
package com.emf.gateway.integration;

import net.jqwik.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests using jqwik.
 * 
 * Purpose: Verify universal properties hold for all valid inputs
 * Category: Property-Based Testing
 */
@Tag("Feature: local-integration-testing")
public class PropertyBasedExampleTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    /**
     * Property 1: JSON:API Response Format Compliance
     * 
     * For any successful response, the response body SHALL conform to 
     * JSON:API specification with a "data" field.
     */
    @Property(tries = 100)
    @Tag("Property 1: JSON:API Response Format Compliance")
    void testJsonApiResponseFormat(@ForAll("validProjects") ProjectInput project) {
        // Arrange
        String token = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", project.name,
                    "description", project.description,
                    "status", project.status
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
        
        // Assert - Response follows JSON:API format
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("data"), "Response must have 'data' field");
        
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data.get("type"), "Data must have 'type' field");
        assertNotNull(data.get("id"), "Data must have 'id' field");
        assertNotNull(data.get("attributes"), "Data must have 'attributes' field");
        
        // Cleanup
        String projectId = (String) data.get("id");
        deleteProject(projectId, token);
    }
    
    /**
     * Property 20: Resource ID Uniqueness
     * 
     * For any resource created via POST, the system SHALL assign a unique ID 
     * that does not conflict with any existing resource.
     */
    @Property(tries = 50)
    @Tag("Property 20: Resource ID Uniqueness")
    void testResourceIdUniqueness(
        @ForAll("validProjects") ProjectInput project1,
        @ForAll("validProjects") ProjectInput project2
    ) {
        // Arrange
        String token = authHelper.getAdminToken();
        
        // Act - Create two projects
        String id1 = createProject(project1, token);
        String id2 = createProject(project2, token);
        
        // Assert - IDs are unique
        assertNotEquals(id1, id2, "Resource IDs must be unique");
        
        // Cleanup
        deleteProject(id1, token);
        deleteProject(id2, token);
    }
    
    // Data generators
    
    @Provide
    Arbitrary<ProjectInput> validProjects() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
            Arbitraries.strings().ofMaxLength(500),
            Arbitraries.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")
        ).as((name, description, status) -> 
            new ProjectInput(name, description, status)
        );
    }
    
    // Helper methods
    
    private String createProject(ProjectInput project, String token) {
        HttpHeaders headers = authHelper.createAuthHeaders(token);
        
        Map<String, Object> projectData = Map.of(
            "data", Map.of(
                "type", "projects",
                "attributes", Map.of(
                    "name", project.name,
                    "description", project.description,
                    "status", project.status
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
        return (String) data.get("id");
    }
    
    private void deleteProject(String projectId, String token) {
        try {
            HttpHeaders headers = authHelper.createAuthHeaders(token);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                GATEWAY_URL + "/api/collections/projects/" + projectId,
                HttpMethod.DELETE,
                request,
                Void.class
            );
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // Test data class
    
    static class ProjectInput {
        final String name;
        final String description;
        final String status;
        
        ProjectInput(String name, String description, String status) {
            this.name = name;
            this.description = description;
            this.status = status;
        }
    }
}
```

## Best Practices

### 1. Test Isolation

Always clean up resources created during tests:

```java
@AfterEach
void cleanup() {
    // Delete all test resources
    createdResourceIds.forEach(id -> {
        try {
            testDataHelper.deleteResource(id);
        } catch (Exception e) {
            // Log but don't fail on cleanup errors
        }
    });
    createdResourceIds.clear();
}
```

### 2. Use Test Helpers

Leverage helper classes for common operations:

```java
// Good - Uses helper
String token = authHelper.getAdminToken();
String projectId = testDataHelper.createProject("Name", "Desc", "ACTIVE");

// Avoid - Manual implementation
// ... lots of boilerplate code ...
```

### 3. Descriptive Test Names

Use clear, descriptive test method names:

```java
// Good
@Test
void testCreateProject_WithValidData_ReturnsCreated() { }

@Test
void testCreateProject_WithMissingName_ReturnsBadRequest() { }

// Avoid
@Test
void test1() { }

@Test
void testProject() { }
```

### 4. Arrange-Act-Assert Pattern

Structure tests clearly:

```java
@Test
void testExample() {
    // Arrange - Set up test data and preconditions
    String token = authHelper.getAdminToken();
    String projectId = createTestProject();
    
    // Act - Perform the action being tested
    ResponseEntity<Map> response = performAction(projectId, token);
    
    // Assert - Verify the results
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
}
```

### 5. Meaningful Assertions

Use descriptive assertion messages:

```java
// Good
assertEquals(HttpStatus.OK, response.getStatusCode(), 
    "Should return 200 OK for valid request");

assertTrue(included.size() >= 2, 
    "Should include at least 2 related tasks");

// Avoid
assertEquals(HttpStatus.OK, response.getStatusCode());
assertTrue(included.size() >= 2);
```

## Running Example Tests

```bash
# Run all examples
mvn test -Dtest=*ExampleTest

# Run specific example
mvn test -Dtest=CrudOperationsExampleTest

# Run with verbose output
mvn test -Dtest=*ExampleTest -X
```

## Additional Resources

- [Integration Tests README](INTEGRATION_TESTS_README.md)
- [Architecture Documentation](INTEGRATION_TESTS_ARCHITECTURE.md)
- [Sample Service API](SAMPLE_SERVICE_API.md)
- [Troubleshooting Guide](INTEGRATION_TESTS_TROUBLESHOOTING.md)
- [JUnit 5 Documentation](https://junit.org/junit5/)
- [jqwik Documentation](https://jqwik.net/)
