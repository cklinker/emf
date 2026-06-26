package io.kelta.worker.health.rule;

import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlowWithoutErrorHandlingRule")
class FlowWithoutErrorHandlingRuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private FlowWithoutErrorHandlingRule rule;

    private void stubFlows(List<Map<String, Object>> rows) {
        rule = new FlowWithoutErrorHandlingRule(objectMapper);
        when(jdbcTemplate.queryForList(anyString(), eq("t1"))).thenReturn(rows);
    }

    @Test
    @DisplayName("flags a flow whose definition has no catch or retry")
    void flagsFlowWithoutErrorHandling() {
        stubFlows(List.of(Map.of("id", "f1", "name", "Order Flow",
                "definition", "{\"states\":[{\"type\":\"Task\",\"resource\":\"CreateRecord\"}]}")));

        List<HealthFinding> findings = rule.evaluate(new HealthContext("t1", null, jdbcTemplate));

        assertThat(findings).singleElement()
                .satisfies(f -> assertThat(f.severity()).isEqualTo(HealthSeverity.WARNING));
    }

    @Test
    @DisplayName("does not flag a flow that defines a catch")
    void ignoresFlowWithCatch() {
        stubFlows(List.of(Map.of("id", "f1", "name", "Order Flow",
                "definition", "{\"states\":[{\"type\":\"Task\",\"catch\":[{\"errorEquals\":[\"All\"]}]}]}")));
        assertThat(rule.evaluate(new HealthContext("t1", null, jdbcTemplate))).isEmpty();
    }

    @Test
    @DisplayName("does not flag a flow that defines a retry")
    void ignoresFlowWithRetry() {
        stubFlows(List.of(Map.of("id", "f1", "name", "Order Flow",
                "definition", "{\"states\":[{\"type\":\"Task\",\"retry\":[{\"maxAttempts\":3}]}]}")));
        assertThat(rule.evaluate(new HealthContext("t1", null, jdbcTemplate))).isEmpty();
    }
}
