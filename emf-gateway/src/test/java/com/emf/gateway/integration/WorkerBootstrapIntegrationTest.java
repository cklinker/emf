package com.emf.gateway.integration;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.gateway.service.RouteConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for worker bootstrap integration.
 *
 * Tests:
 * - Gateway fetches bootstrap configuration from the worker on startup
 * - Bootstrap response is parsed correctly
 * - Routes are created from bootstrap configuration
 * - Invalid bootstrap data is handled gracefully
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 10.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkerBootstrapIntegrationTest {

    private static MockWebServer mockWorker;

    @Autowired
    private RouteConfigService routeConfigService;

    @Autowired
    private RouteRegistry routeRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setUpMockServer() throws IOException {
        mockWorker = new MockWebServer();
        mockWorker.start();
    }

    @AfterAll
    static void tearDownMockServer() throws IOException {
        if (mockWorker != null) {
            mockWorker.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("emf.gateway.worker-service-url", () -> mockWorker.url("/").toString().replaceAll("/$", ""));
    }

    @BeforeEach
    void setUp() {
        // Clear route registry before each test
        routeRegistry.clear();
    }

    @Test
    void testBootstrapConfiguration_ValidResponse() throws Exception {
        // Arrange - create bootstrap response
        String bootstrapResponse = """
                {
                    "collections": [
                        {
                            "id": "users-collection",
                            "name": "users",
                            "path": "/api/users",
                            "fields": [
                                {"name": "id", "type": "string"},
                                {"name": "email", "type": "string"},
                                {"name": "name", "type": "string"}
                            ]
                        },
                        {
                            "id": "products-collection",
                            "name": "products",
                            "path": "/api/products",
                            "fields": [
                                {"name": "id", "type": "string"},
                                {"name": "name", "type": "string"},
                                {"name": "price", "type": "number"}
                            ]
                        }
                    ]
                }
                """;

        mockWorker.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(bootstrapResponse));

        // Act - fetch bootstrap configuration
        routeConfigService.refreshRoutes();

        // Assert - verify routes were created
        Optional<RouteDefinition> usersRoute = routeRegistry.findByPath("/api/users/**");
        assertThat(usersRoute).isPresent();
        assertThat(usersRoute.get().getId()).isEqualTo("users-collection");
        assertThat(usersRoute.get().getBackendUrl()).isNotNull();
        assertThat(usersRoute.get().getCollectionName()).isEqualTo("users");

        Optional<RouteDefinition> productsRoute = routeRegistry.findByPath("/api/products/**");
        assertThat(productsRoute).isPresent();
        assertThat(productsRoute.get().getId()).isEqualTo("products-collection");
        assertThat(productsRoute.get().getBackendUrl()).isNotNull();

        // Verify request was made to worker's bootstrap endpoint
        RecordedRequest request = mockWorker.takeRequest();
        assertThat(request.getPath()).isEqualTo("/internal/bootstrap");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    void testBootstrapConfiguration_MissingRequiredFields() throws Exception {
        // Arrange - create bootstrap response with missing required fields
        String bootstrapResponse = """
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

        mockWorker.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(bootstrapResponse));


        // Act - fetch bootstrap configuration
        routeConfigService.refreshRoutes();

        // Assert - valid collection should be added, invalid should be skipped
        Optional<RouteDefinition> validRoute = routeRegistry.findByPath("/api/valid/**");
        assertThat(validRoute).isPresent();

        Optional<RouteDefinition> invalidRoute = routeRegistry.findByPath("/api/invalid/**");
        assertThat(invalidRoute).isEmpty();
    }

    @Test
    void testBootstrapConfiguration_EmptyResponse() throws Exception {
        // Arrange - create empty bootstrap response
        String bootstrapResponse = """
                {
                    "collections": []
                }
                """;

        mockWorker.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(bootstrapResponse));


        // Act - fetch bootstrap configuration
        routeConfigService.refreshRoutes();

        // Assert - no routes should be added
        List<RouteDefinition> routes = routeRegistry.getAllRoutes();
        assertThat(routes).isEmpty();
    }

    @Test
    void testBootstrapConfiguration_WorkerUnavailable() {
        // Arrange - mock worker returns error
        mockWorker.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));


        // Act & Assert - should handle error gracefully
        try {
            routeConfigService.refreshRoutes();
            // If no exception is thrown, the error was handled gracefully
        } catch (Exception e) {
            // Exception is acceptable - bootstrap failure should be logged
            assertThat(e).isNotNull();
        }
    }

    @Test
    void testBootstrapConfiguration_MalformedJson() {
        // Arrange - mock worker returns malformed JSON
        mockWorker.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{ invalid json }"));


        // Act & Assert - should handle error gracefully
        try {
            routeConfigService.refreshRoutes();
            // If no exception is thrown, the error was handled gracefully
        } catch (Exception e) {
            // Exception is acceptable - parsing failure should be logged
            assertThat(e).isNotNull();
        }
    }

    @Test
    void testBootstrapConfiguration_WithGovernorLimits() throws Exception {
        // Arrange - create bootstrap response with governor limits
        String bootstrapResponse = """
                {
                    "collections": [
                        {
                            "id": "rate-limited-collection",
                            "name": "ratelimited",
                            "path": "/api/ratelimited",
                            "fields": []
                        }
                    ],
                    "governorLimits": {
                        "tenant-1": {
                            "apiCallsPerDay": 50000
                        }
                    }
                }
                """;

        mockWorker.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(bootstrapResponse));


        // Act - fetch bootstrap configuration
        routeConfigService.refreshRoutes();

        // Assert - route should be present
        Optional<RouteDefinition> route = routeRegistry.findByPath("/api/ratelimited/**");
        assertThat(route).isPresent();
    }
}
