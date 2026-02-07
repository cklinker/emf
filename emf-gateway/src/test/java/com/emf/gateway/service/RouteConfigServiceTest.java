package com.emf.gateway.service;

import com.emf.gateway.config.*;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RouteConfigService.
 * 
 * Tests bootstrap configuration fetching, parsing, and validation.
 */
class RouteConfigServiceTest {
    
    private MockWebServer mockWebServer;
    private RouteConfigService routeConfigService;
    private RouteRegistry routeRegistry;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        routeRegistry = new RouteRegistry();
        
        routeConfigService = new RouteConfigService(
            WebClient.builder(),
            routeRegistry,
            baseUrl,
            "/control/bootstrap"
        );
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void testFetchBootstrapConfig_Success() {
        // Arrange
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "users-collection",
                  "name": "users",
                  "serviceId": "service-1",
                  "path": "/api/users",
                  "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "email", "type": "string"}
                  ]
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        Mono<BootstrapConfig> result = routeConfigService.fetchBootstrapConfig();
        
        // Assert
        StepVerifier.create(result)
            .assertNext(config -> {
                assertNotNull(config);
                assertNotNull(config.getServices());
                assertEquals(1, config.getServices().size());
                assertEquals("service-1", config.getServices().get(0).getId());
                
                assertNotNull(config.getCollections());
                assertEquals(1, config.getCollections().size());
                assertEquals("users-collection", config.getCollections().get(0).getId());
                
                assertNotNull(config.getAuthorization());
            })
            .verifyComplete();
    }
    
    @Test
    void testFetchBootstrapConfig_ServerError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));
        
        // Act
        Mono<BootstrapConfig> result = routeConfigService.fetchBootstrapConfig();
        
        // Assert
        StepVerifier.create(result)
            .expectError()
            .verify();
    }
    
    @Test
    void testFetchBootstrapConfig_InvalidJson() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setBody("{ invalid json }")
            .addHeader("Content-Type", "application/json"));
        
        // Act
        Mono<BootstrapConfig> result = routeConfigService.fetchBootstrapConfig();
        
        // Assert
        StepVerifier.create(result)
            .expectError()
            .verify();
    }
    
    @Test
    void testRefreshRoutes_ValidConfiguration() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                },
                {
                  "id": "service-2",
                  "name": "Post Service",
                  "baseUrl": "http://post-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "users-collection",
                  "name": "users",
                  "serviceId": "service-1",
                  "path": "/api/users",
                  "fields": []
                },
                {
                  "id": "posts-collection",
                  "name": "posts",
                  "serviceId": "service-2",
                  "path": "/api/posts",
                  "fields": []
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        
        // Give async processing time to complete
        Thread.sleep(500);
        
        // Assert
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(2, routes.size());
        
        // Verify first route (path has /** wildcard added)
        RouteDefinition usersRoute = routeRegistry.findByPath("/api/users/**").orElse(null);
        assertNotNull(usersRoute);
        assertEquals("users-collection", usersRoute.getId());
        assertEquals("service-1", usersRoute.getServiceId());
        assertEquals("/api/users/**", usersRoute.getPath());
        assertEquals("http://user-service:8080", usersRoute.getBackendUrl());
        assertEquals("users", usersRoute.getCollectionName());
        
        // Verify second route (path has /** wildcard added)
        RouteDefinition postsRoute = routeRegistry.findByPath("/api/posts/**").orElse(null);
        assertNotNull(postsRoute);
        assertEquals("posts-collection", postsRoute.getId());
        assertEquals("service-2", postsRoute.getServiceId());
    }
    
    @Test
    void testRefreshRoutes_MissingServiceId() throws InterruptedException {
        // Arrange - collection with missing serviceId
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "invalid-collection",
                  "name": "invalid",
                  "path": "/api/invalid",
                  "fields": []
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        Thread.sleep(500);
        
        // Assert - invalid route should be skipped
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(0, routes.size());
    }
    
    @Test
    void testRefreshRoutes_MissingPath() throws InterruptedException {
        // Arrange - collection with missing path
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "invalid-collection",
                  "name": "invalid",
                  "serviceId": "service-1",
                  "fields": []
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        Thread.sleep(500);
        
        // Assert - invalid route should be skipped
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(0, routes.size());
    }
    
    @Test
    void testRefreshRoutes_ServiceNotFound() throws InterruptedException {
        // Arrange - collection references non-existent service
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "orphan-collection",
                  "name": "orphan",
                  "serviceId": "non-existent-service",
                  "path": "/api/orphan",
                  "fields": []
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        Thread.sleep(500);
        
        // Assert - route with missing service should be skipped
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(0, routes.size());
    }
    
    @Test
    void testRefreshRoutes_EmptyCollections() throws InterruptedException {
        // Arrange - no collections
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        Thread.sleep(500);
        
        // Assert
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(0, routes.size());
    }
    
    @Test
    void testRefreshRoutes_MixedValidAndInvalid() throws InterruptedException {
        // Arrange - mix of valid and invalid routes
        String jsonResponse = """
            {
              "services": [
                {
                  "id": "service-1",
                  "name": "User Service",
                  "baseUrl": "http://user-service:8080"
                }
              ],
              "collections": [
                {
                  "id": "valid-collection",
                  "name": "valid",
                  "serviceId": "service-1",
                  "path": "/api/valid",
                  "fields": []
                },
                {
                  "id": "invalid-collection",
                  "name": "invalid",
                  "serviceId": "service-1",
                  "fields": []
                }
              ],
              "authorization": {
                "roles": [],
                "policies": [],
                "routePolicies": [],
                "fieldPolicies": []
              }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        routeConfigService.refreshRoutes();
        Thread.sleep(500);
        
        // Assert - only valid route should be added
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertEquals(1, routes.size());
        assertEquals("valid-collection", routes.get(0).getId());
    }
}
