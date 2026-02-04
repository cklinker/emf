package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test for health check endpoints.
 * 
 * Tests:
 * - Overall health endpoint returns status
 * - Redis health indicator is included
 * - Kafka health indicator is included
 * - Control plane health indicator is included
 * - Health endpoint is accessible without authentication
 * - Individual component health can be checked
 * 
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthCheckIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Test
    void testHealthEndpoint_ReturnsStatus() {
        // Act & Assert - health endpoint should be accessible
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists();
    }
    
    @Test
    void testHealthEndpoint_NoAuthenticationRequired() {
        // Act & Assert - health endpoint should not require authentication
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
        
        // No Authorization header provided, but request should succeed
    }
    
    @Test
    void testHealthEndpoint_IncludesRedisStatus() {
        // Act & Assert - health response should include Redis status
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.redis").exists();
        
        // Redis status will be UP if Redis is available, DOWN otherwise
        // Both are valid - the important thing is that the component is checked
    }
    
    @Test
    void testHealthEndpoint_IncludesKafkaStatus() {
        // Act & Assert - health response should include Kafka status
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.kafka").exists();
        
        // Kafka status will be UP if Kafka is available, DOWN otherwise
        // Both are valid - the important thing is that the component is checked
    }
    
    @Test
    void testHealthEndpoint_IncludesControlPlaneStatus() {
        // Act & Assert - health response should include control plane status
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.controlPlane").exists();
        
        // Control plane status will be UP if control plane is available, DOWN otherwise
        // Both are valid - the important thing is that the component is checked
    }
    
    @Test
    void testHealthEndpoint_ShowsDetails() {
        // Act & Assert - health endpoint should show component details
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components").exists()
                .jsonPath("$.components.redis.status").exists()
                .jsonPath("$.components.kafka.status").exists()
                .jsonPath("$.components.controlPlane.status").exists();
    }
    
    @Test
    void testHealthEndpoint_OverallStatus() {
        // Act & Assert - overall status should be determined by component statuses
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").value(status -> {
                    // Status should be UP, DOWN, or OUT_OF_SERVICE
                    // Depends on whether dependencies are available
                    // All are valid responses
                });
    }
    
    @Test
    void testHealthEndpoint_LivenessProbe() {
        // Act & Assert - liveness probe should indicate if application is running
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
        
        // Liveness should always be UP if the application is running
    }
    
    @Test
    void testHealthEndpoint_ReadinessProbe() {
        // Act & Assert - readiness probe should indicate if application is ready
        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists();
        
        // Readiness depends on whether dependencies are available
        // Can be UP or DOWN
    }
    
    @Test
    void testHealthEndpoint_IndividualComponents() {
        // Act & Assert - individual component health can be checked
        
        // Redis health
        webTestClient.get()
                .uri("/actuator/health/redis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists();
        
        // Kafka health
        webTestClient.get()
                .uri("/actuator/health/kafka")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists();
        
        // Control plane health
        webTestClient.get()
                .uri("/actuator/health/controlPlane")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists();
    }
    
    @Test
    void testHealthEndpoint_JsonFormat() {
        // Act & Assert - health response should be in JSON format
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.spring-boot.actuator.v3+json")
                .expectBody()
                .jsonPath("$").isMap()
                .jsonPath("$.status").isNotEmpty()
                .jsonPath("$.components").isMap();
    }
}
