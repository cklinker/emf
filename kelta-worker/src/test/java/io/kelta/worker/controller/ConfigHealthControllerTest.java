package io.kelta.worker.controller;

import io.kelta.worker.health.ConfigHealthAnalyzer;
import io.kelta.worker.health.ConfigHealthReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigHealthController")
class ConfigHealthControllerTest {

    @Mock
    private ConfigHealthAnalyzer analyzer;

    @Test
    @DisplayName("wraps the report in a data envelope with no attributes key (FLS-safe)")
    void healthReturnsWrappedReport() {
        when(analyzer.analyze("t1")).thenReturn(ConfigHealthReport.from(List.of(), 6));
        ConfigHealthController controller = new ConfigHealthController(analyzer);

        ResponseEntity<Map<String, Object>> response = controller.health("t1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("score", 100).doesNotContainKey("attributes");
    }
}
