package com.emf.gateway.service;

import com.emf.gateway.config.BootstrapConfig;
import com.emf.gateway.ratelimit.TenantGovernorLimitCache;
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
    private TenantGovernorLimitCache governorLimitCache;

    private static final String WORKER_SERVICE_URL = "http://emf-worker:80";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        routeRegistry = new RouteRegistry();
        governorLimitCache = new TenantGovernorLimitCache();

        routeConfigService = new RouteConfigService(
            WebClient.builder(),
            routeRegistry,
            governorLimitCache,
            baseUrl,
            "/control/bootstrap",
            WORKER_SERVICE_URL
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
              "collections": [
                {
                  "id": "users-collection",
                  "name": "users",
                  "path": "/api/users",
                  "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "email", "type": "string"}
                  ]
                }
              ]
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

                assertNotNull(config.getCollections());
                assertEquals(1, config.getCollections().size());
                assertEquals("users-collection", config.getCollections().get(0).getId());
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
              "collections": [
                {
                  "id": "users-collection",
                  "name": "users",
                  "path": "/api/users",
                  "fields": []
                },
                {
                  "id": "posts-collection",
                  "name": "posts",
                  "path": "/api/posts",
                  "fields": []
                }
              ]
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
        assertEquals("/api/users/**", usersRoute.getPath());
        assertEquals(WORKER_SERVICE_URL, usersRoute.getBackendUrl());
        assertEquals("users", usersRoute.getCollectionName());

        // Verify second route (path has /** wildcard added)
        RouteDefinition postsRoute = routeRegistry.findByPath("/api/posts/**").orElse(null);
        assertNotNull(postsRoute);
        assertEquals("posts-collection", postsRoute.getId());
    }

    @Test
    void testRefreshRoutes_AlwaysUsesConfiguredServiceUrl() throws InterruptedException {
        // Arrange - bootstrap response includes pod-specific URLs, but gateway
        // should ignore them and always use the configured K8s Service URL.
        // Pod IPs are ephemeral and become stale when pods restart.
        String jsonResponse = """
            {
              "collections": [
                {
                  "id": "product-collection",
                  "name": "product",
                  "path": "/api/product",
                  "workerBaseUrl": "http://10.1.150.147:8080",
                  "fields": []
                },
                {
                  "id": "tasks-collection",
                  "name": "tasks",
                  "path": "/api/tasks",
                  "fields": []
                }
              ]
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
        assertEquals(2, routes.size());

        // Even though bootstrap included a pod IP, gateway should use configured service URL
        RouteDefinition productRoute = routeRegistry.findByPath("/api/product/**").orElse(null);
        assertNotNull(productRoute);
        assertEquals(WORKER_SERVICE_URL, productRoute.getBackendUrl());

        // Collection without workerBaseUrl also uses configured service URL
        RouteDefinition tasksRoute = routeRegistry.findByPath("/api/tasks/**").orElse(null);
        assertNotNull(tasksRoute);
        assertEquals(WORKER_SERVICE_URL, tasksRoute.getBackendUrl());
    }

    @Test
    void testRefreshRoutes_MissingPath() throws InterruptedException {
        // Arrange - collection with missing path
        String jsonResponse = """
            {
              "collections": [
                {
                  "id": "invalid-collection",
                  "name": "invalid",
                  "fields": []
                }
              ]
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
    void testRefreshRoutes_EmptyCollections() throws InterruptedException {
        // Arrange - no collections
        String jsonResponse = """
            {
              "collections": []
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
              "collections": [
                {
                  "id": "valid-collection",
                  "name": "valid",
                  "path": "/api/valid",
                  "fields": []
                },
                {
                  "id": "invalid-collection",
                  "name": "invalid",
                  "fields": []
                }
              ]
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
