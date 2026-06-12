package io.kelta.worker.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScheduledJobRepository")
class ScheduledJobRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private ScheduledJobRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new ScheduledJobRepository(jdbcTemplate);
    }

    /**
     * Regression: jsonb columns come back from the driver as a non-String wrapper
     * (PGobject). If left un-normalized, the scheduled executor calls
     * {@code objectMapper.writeValueAsString(pgObject)}, which serializes the wrapper
     * instead of the JSON and the flow definition's {@code StartAt} appears missing,
     * breaking every scheduled FLOW job. findFlowById must hand back the JSON string.
     */
    @Test
    @DisplayName("findFlowById normalizes jsonb definition + trigger_config to their JSON string")
    void findFlowByIdNormalizesJsonbToString() {
        String definitionJson = "{\"StartAt\":\"InitTimestamp\",\"States\":{}}";
        String triggerConfigJson = "{\"cron\":\"0 0 */4 * * *\",\"timezone\":\"UTC\"}";
        // Stand-in for the driver's PGobject: a non-String whose toString() is the JSON.
        Object definitionJsonb = new Object() {
            @Override public String toString() { return definitionJson; }
        };
        Object triggerConfigJsonb = new Object() {
            @Override public String toString() { return triggerConfigJson; }
        };
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "flow-1");
        row.put("tenant_id", "t1");
        row.put("definition", definitionJsonb);
        row.put("trigger_config", triggerConfigJsonb);
        row.put("active", true);
        when(jdbcTemplate.queryForList(contains("FROM flow WHERE id"), eq("flow-1")))
                .thenReturn(List.of(row));

        var result = repository.findFlowById("flow-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("definition"))
                .isInstanceOf(String.class)
                .isEqualTo(definitionJson);
        assertThat(result.get().get("trigger_config"))
                .isInstanceOf(String.class)
                .isEqualTo(triggerConfigJson);
    }

    @Test
    @DisplayName("findFlowById returns empty when the flow does not exist")
    void findFlowByIdReturnsEmptyWhenNotFound() {
        when(jdbcTemplate.queryForList(contains("FROM flow WHERE id"), anyString()))
                .thenReturn(List.of());

        assertThat(repository.findFlowById("missing")).isEmpty();
    }

    @Test
    @DisplayName("findFlowById tolerates a null definition without throwing")
    void findFlowByIdToleratesNullDefinition() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "flow-1");
        row.put("tenant_id", "t1");
        row.put("definition", null);
        row.put("trigger_config", null);
        row.put("active", true);
        when(jdbcTemplate.queryForList(contains("FROM flow WHERE id"), eq("flow-1")))
                .thenReturn(List.of(row));

        var result = repository.findFlowById("flow-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("definition")).isNull();
    }
}
