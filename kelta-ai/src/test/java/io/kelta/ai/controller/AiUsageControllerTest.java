package io.kelta.ai.controller;

import io.kelta.ai.repository.TokenUsageRepository;
import io.kelta.ai.service.TokenTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiUsageController")
class AiUsageControllerTest {

    @Mock
    private TokenTrackingService tokenTrackingService;

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    private AiUsageController controller;

    @BeforeEach
    void setUp() {
        controller = new AiUsageController(tokenTrackingService, tokenUsageRepository);
    }

    @Test
    @DisplayName("returns usage data with current month stats and history")
    void returnsUsageData() {
        when(tokenTrackingService.getCurrentMonthUsage("tenant-1")).thenReturn(50000L);
        when(tokenTrackingService.getTokenLimit("tenant-1")).thenReturn(1000000L);
        when(tokenTrackingService.isAiEnabled("tenant-1")).thenReturn(true);
        when(tokenUsageRepository.getUsageHistory("tenant-1", 12))
                .thenReturn(Map.of("2026-04", Map.of("input", 30000L, "output", 20000L)));

        ResponseEntity<Map<String, Object>> response = controller.getUsage("tenant-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("currentMonthUsage")).isEqualTo(50000L);
        assertThat(data.get("tokenLimit")).isEqualTo(1000000L);
        assertThat(data.get("aiEnabled")).isEqualTo(true);
        assertThat(data.get("history")).isNotNull();
    }

    @Test
    @DisplayName("returns zero usage for new tenant")
    void returnsZeroForNewTenant() {
        when(tokenTrackingService.getCurrentMonthUsage("new-tenant")).thenReturn(0L);
        when(tokenTrackingService.getTokenLimit("new-tenant")).thenReturn(1000000L);
        when(tokenTrackingService.isAiEnabled("new-tenant")).thenReturn(true);
        when(tokenUsageRepository.getUsageHistory("new-tenant", 12)).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> response = controller.getUsage("new-tenant");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("currentMonthUsage")).isEqualTo(0L);
    }
}
