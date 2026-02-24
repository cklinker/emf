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
 * Unit tests for WorkerHealthIndicator.
 *
 * Tests verify that the health indicator correctly reports Worker service connectivity.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerHealthIndicator Tests")
class WorkerHealthIndicatorTest {

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

    private WorkerHealthIndicator healthIndicator;

    private static final String WORKER_URL = "http://emf-worker:80";
    private static final String HEALTH_PATH = "/actuator/health";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        healthIndicator = new WorkerHealthIndicator(
            webClientBuilder,
            WORKER_URL
        );
    }

    @Test
    @DisplayName("Should report UP when Worker is reachable")
    void shouldReportUpWhenWorkerIsReachable() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(HEALTH_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
        assertThat(health.getDetails()).containsEntry("url", WORKER_URL);
        assertThat(health.getDetails()).containsEntry("endpoint", HEALTH_PATH);
    }

    @Test
    @DisplayName("Should report DOWN when Worker is unreachable")
    void shouldReportDownWhenWorkerIsUnreachable() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(HEALTH_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
            .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("url", WORKER_URL);
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Connection refused");
    }

    @Test
    @DisplayName("Should report DOWN when request times out")
    void shouldReportDownWhenRequestTimesOut() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(HEALTH_PATH)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.never());

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
    }
}
