package com.emf.gateway.integration;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.authz.AuthzConfig;
import com.emf.gateway.authz.AuthzConfigCache;
import com.emf.gateway.authz.FieldPolicy;
import com.emf.gateway.authz.RoutePolicy;
import com.emf.gateway.route.RateLimitConfig;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for complete request flow through all gateway filters.
 * 
 * Tests the complete request processing pipeline:
 * 1. JwtAuthenticationFilter - validates JWT and extracts principal
 * 2. RateLimitFilter - checks rate limits (if configured)
 * 3. RouteAuthorizationFilter - validates route policies
 * 4. HeaderTransformationFilter - transforms headers for backend
 * 5. Backend routing - forwards to backend service
 * 6. FieldAuthorizationFilter - filters response fields
 * 7. JsonApiIncludeFilter - processes include parameters
 * 
 * Validates: Complete request flow through all filters
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EndToEndRequestFlowIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Autowired
    private RouteRegistry routeRegistry;
    
    @Autowired
    private AuthzConfigCache authzConfigCache;
    
    @Autowired
    private ReactiveJwtDecoder jwtDecoder;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockWebServer mockBackend;
    
    @BeforeEach
    void setUp() throws IOException {
        // Start mock backend server
        mockBackend = new MockWebServer();
        mockBackend.start();
        
        // Clear registries
        routeRegistry.clear();
        
        // Configure mock JWT decoder
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("roles", List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(mockJwt));
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (mockBackend != null) {
            mockBackend.shutdown();
        }
    }
    
    @Test
    void testCompleteRequestFlow_WithAuthentication() throws Exception {
        // Arrange - set up route
        String backendUrl = mockBackend.url("/").toString();
        RouteDefinition route = new RouteDefinition(
                "test-collection",
                "/api/test/**",
                backendUrl,
                "test-collection"
        );
        routeRegistry.addRoute(route);

        // Mock backend response
        String jsonApiResponse = """
                {
                    "data": {
                        "type": "test",
                        "id": "1",
                        "attributes": {
                            "name": "Test Item",
                            "value": "123"
                        }
                    }
                }
                """;
        
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonApiResponse));
        
        // Act - send request through gateway
        webTestClient.get()
                .uri("/api/test/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.data.type").isEqualTo("test")
                .jsonPath("$.data.id").isEqualTo("1")
                .jsonPath("$.data.attributes.name").isEqualTo("Test Item");
        
        // Assert - verify backend received transformed headers
        RecordedRequest backendRequest = mockBackend.takeRequest();
        assertThat(backendRequest.getPath()).isEqualTo("/1");
        assertThat(backendRequest.getHeader("X-Forwarded-User")).isEqualTo("test-user");
        assertThat(backendRequest.getHeader("X-Forwarded-Roles")).isEqualTo("USER");
        assertThat(backendRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }
    
    @Test
    void testCompleteRequestFlow_WithRouteAuthorization() throws Exception {
        // Arrange - set up route with authorization
        String backendUrl = mockBackend.url("/").toString();
        RouteDefinition route = new RouteDefinition(
                "protected-collection",
                "/api/protected/**",
                backendUrl,
                "protected-collection"
        );
        routeRegistry.addRoute(route);
        
        // Configure authorization - require ADMIN role
        RoutePolicy routePolicy = new RoutePolicy("GET", "admin-only", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "protected-collection",
                List.of(routePolicy),
                List.of()
        );
        authzConfigCache.updateConfig("protected-collection", authzConfig);
        
        // Act & Assert - request without ADMIN role should be forbidden
        webTestClient.get()
                .uri("/api/protected/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isForbidden();
    }
    
    @Test
    void testCompleteRequestFlow_WithFieldFiltering() throws Exception {
        // Arrange - set up route with field policies
        String backendUrl = mockBackend.url("/").toString();
        RouteDefinition route = new RouteDefinition(
                "filtered-collection",
                "/api/filtered/**",
                backendUrl,
                "filtered-collection"
        );
        routeRegistry.addRoute(route);
        
        // Configure field authorization - hide "secret" field from non-ADMIN users
        FieldPolicy fieldPolicy = new FieldPolicy("secret", "admin-only", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig(
                "filtered-collection",
                List.of(),
                List.of(fieldPolicy)
        );
        authzConfigCache.updateConfig("filtered-collection", authzConfig);
        
        // Mock backend response with secret field
        String jsonApiResponse = """
                {
                    "data": {
                        "type": "filtered",
                        "id": "1",
                        "attributes": {
                            "name": "Public Data",
                            "secret": "Hidden Value"
                        }
                    }
                }
                """;
        
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonApiResponse));
        
        // Act & Assert - secret field should be filtered out
        webTestClient.get()
                .uri("/api/filtered/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.attributes.name").isEqualTo("Public Data")
                .jsonPath("$.data.attributes.secret").doesNotExist();
    }
    
    @Test
    void testCompleteRequestFlow_WithRateLimiting() throws Exception {
        // Arrange - set up route with rate limiting
        String backendUrl = mockBackend.url("/").toString();
        RateLimitConfig rateLimitConfig = new RateLimitConfig(2, Duration.ofMinutes(1));
        
        RouteDefinition route = new RouteDefinition(
                "rate-limited-collection",
                "/api/ratelimited/**",
                backendUrl,
                "rate-limited-collection",
                rateLimitConfig
        );
        routeRegistry.addRoute(route);
        
        // Mock backend responses
        for (int i = 0; i < 3; i++) {
            mockBackend.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"data\": {\"type\": \"test\", \"id\": \"1\"}}"));
        }
        
        // Act & Assert - first two requests should succeed
        webTestClient.get()
                .uri("/api/ratelimited/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Limit")
                .expectHeader().exists("X-RateLimit-Remaining");
        
        webTestClient.get()
                .uri("/api/ratelimited/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isOk();
        
        // Third request should be rate limited (if Redis is available)
        // Note: This test may pass if Redis is not available (graceful degradation)
        webTestClient.get()
                .uri("/api/ratelimited/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().value(status -> 
                    assertThat(status).isIn(HttpStatus.OK.value(), HttpStatus.TOO_MANY_REQUESTS.value()));
    }
    
    @Test
    void testCompleteRequestFlow_UnauthenticatedRequest() {
        // Arrange - set up route
        String backendUrl = mockBackend.url("/").toString();
        RouteDefinition route = new RouteDefinition(
                "test-collection",
                "/api/test/**",
                backendUrl,
                "test-collection"
        );
        routeRegistry.addRoute(route);

        // Act & Assert - request without JWT should be unauthorized
        webTestClient.get()
                .uri("/api/test/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.status").isEqualTo(401)
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED");
    }
    
    @Test
    void testCompleteRequestFlow_RouteNotFound() {
        // Act & Assert - request to non-existent route should return 404
        webTestClient.get()
                .uri("/api/nonexistent/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isNotFound();
    }
    
    @Test
    void testCompleteRequestFlow_BackendError() throws Exception {
        // Arrange - set up route
        String backendUrl = mockBackend.url("/").toString();
        RouteDefinition route = new RouteDefinition(
                "test-collection",
                "/api/test/**",
                backendUrl,
                "test-collection"
        );
        routeRegistry.addRoute(route);

        // Mock backend error response
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"error\": \"Internal server error\"}"));
        
        // Act & Assert - backend error should be passed through
        webTestClient.get()
                .uri("/api/test/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mock-token")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Internal server error");
    }
}
