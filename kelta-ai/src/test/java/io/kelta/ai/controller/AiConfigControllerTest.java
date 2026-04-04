package io.kelta.ai.controller;

import io.kelta.ai.repository.AiConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiConfigController")
class AiConfigControllerTest {

    @Mock
    private AiConfigRepository configRepository;

    private AiConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new AiConfigController(configRepository);
    }

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("returns tenant config merged with global defaults")
        void returnsTenantConfigWithGlobalDefaults() {
            Map<String, String> tenantConfig = new HashMap<>();
            tenantConfig.put("anthropic.model", "claude-sonnet-4-20250514");

            Map<String, String> globalConfig = new HashMap<>();
            globalConfig.put("anthropic.maxTokens", "8192");
            globalConfig.put("aiEnabled", "false");

            when(configRepository.getAllConfig("tenant-1")).thenReturn(tenantConfig);
            when(configRepository.getAllConfig("0")).thenReturn(globalConfig);

            ResponseEntity<Map<String, Object>> response = controller.getConfig("tenant-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("model")).isEqualTo("claude-sonnet-4-20250514");
            assertThat(data.get("maxTokens")).isEqualTo("8192");
            assertThat(data.get("aiEnabled")).isEqualTo("false");
        }

        @Test
        @DisplayName("returns hardcoded defaults when no config exists")
        void returnsDefaultsWhenNoConfig() {
            when(configRepository.getAllConfig("tenant-1")).thenReturn(new HashMap<>());
            when(configRepository.getAllConfig("0")).thenReturn(new HashMap<>());

            ResponseEntity<Map<String, Object>> response = controller.getConfig("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("model")).isEqualTo("claude-sonnet-4-20250514");
            assertThat(data.get("maxTokens")).isEqualTo("4096");
            assertThat(data.get("temperature")).isEqualTo("0.7");
            assertThat(data.get("aiTokensPerMonth")).isEqualTo("1000000");
            assertThat(data.get("aiEnabled")).isEqualTo("true");
        }

        @Test
        @DisplayName("tenant config overrides global config")
        void tenantOverridesGlobal() {
            Map<String, String> tenantConfig = new HashMap<>();
            tenantConfig.put("aiEnabled", "false");

            Map<String, String> globalConfig = new HashMap<>();
            globalConfig.put("aiEnabled", "true");

            when(configRepository.getAllConfig("tenant-1")).thenReturn(tenantConfig);
            when(configRepository.getAllConfig("0")).thenReturn(globalConfig);

            ResponseEntity<Map<String, Object>> response = controller.getConfig("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("aiEnabled")).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("updateConfig")
    class UpdateConfig {

        @Test
        @DisplayName("maps UI field names to config keys and persists")
        void mapsFieldNamesToConfigKeys() {
            Map<String, Object> body = Map.of(
                    "model", "claude-opus-4-20250514",
                    "maxTokens", "16384"
            );

            when(configRepository.getAllConfig("tenant-1")).thenReturn(new HashMap<>());
            when(configRepository.getAllConfig("0")).thenReturn(new HashMap<>());

            controller.updateConfig("tenant-1", body);

            verify(configRepository).setConfig("tenant-1", "anthropic.model", "claude-opus-4-20250514");
            verify(configRepository).setConfig("tenant-1", "anthropic.maxTokens", "16384");
        }

        @Test
        @DisplayName("returns updated config after save")
        void returnsUpdatedConfig() {
            Map<String, Object> body = Map.of("aiEnabled", "false");

            Map<String, String> updatedConfig = new HashMap<>();
            updatedConfig.put("aiEnabled", "false");
            when(configRepository.getAllConfig("tenant-1")).thenReturn(updatedConfig);
            when(configRepository.getAllConfig("0")).thenReturn(new HashMap<>());

            ResponseEntity<Map<String, Object>> response = controller.updateConfig("tenant-1", body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
