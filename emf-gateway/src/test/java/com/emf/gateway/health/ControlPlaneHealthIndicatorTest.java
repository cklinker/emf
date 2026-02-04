package com.emf.gateway.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ControlPlaneHealthIndicator.
 * 
 * Tests verify that the health indicator correctly reports Control Plane connectivity status.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ControlPlaneHealthIndicator Tests")
class ControlPlaneHealthIndicatorTest {
    
    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;
    
    private ControlPlaneHealthIndicator healthIndicator;
    
    private static final String CONTROL_PLANE_URL = "http://localhost:8080";
    private static final String BOOTSTRAP_PATH = "/control/bootstrap";
    
    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        healthIndicator = new ControlPlaneHealthIndicator(
            webClientBuilder,
            CONTROL_PLANE_URL,
            BOOTSTRAP_PATH
        );
    }
    
    @Test
    @DisplayName("Should report UP when Control Plane is reachable")
    void shouldReportUpWhenControlPlaneIsReachable() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(BOOTSTRAP_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
        assertThat(health.getDetails()).containsEntry("url", CONTROL_PLANE_URL);
        assertThat(health.getDetails()).containsEntry("endpoint", BOOTSTRAP_PATH);
        
        verify(webClient).get();
        verify(requestHeadersUriSpec).uri(BOOTSTRAP_PATH);
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).toBodilessEntity();
    }
    
    @Test
    @DisplayName("Should report DOWN when Control Plane is unreachable")
    void shouldReportDownWhenControlPlaneIsUnreachable() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(BOOTSTRAP_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
            .thenReturn(Mono.error(new RuntimeException("Connection refused")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("url", CONTROL_PLANE_URL);
        assertThat(health.getDetails()).containsEntry("endpoint", BOOTSTRAP_PATH);
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Connection refused");
    }
    
    @Test
    @DisplayName("Should report DOWN when request times out")
    void shouldReportDownWhenRequestTimesOut() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(BOOTSTRAP_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.never()); // Never completes, will timeout
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    @DisplayName("Should report DOWN when HTTP error occurs")
    void shouldReportDownWhenHttpErrorOccurs() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(BOOTSTRAP_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
            .thenReturn(Mono.error(new RuntimeException("500 Internal Server Error")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("message", "500 Internal Server Error");
    }
    
    @Test
    @DisplayName("Should use configured URL and path")
    void shouldUseConfiguredUrlAndPath() {
        // Given
        String customUrl = "http://custom-control-plane:9090";
        String customPath = "/api/health";
        
        when(webClientBuilder.baseUrl(customUrl)).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        ControlPlaneHealthIndicator customIndicator = new ControlPlaneHealthIndicator(
            webClientBuilder,
            customUrl,
            customPath
        );
        
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(customPath)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());
        
        // When
        Health health = customIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("url", customUrl);
        assertThat(health.getDetails()).containsEntry("endpoint", customPath);
        
        verify(webClientBuilder).baseUrl(customUrl);
        verify(requestHeadersUriSpec).uri(customPath);
    }
}
