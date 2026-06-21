package io.kelta.ai.repository;

import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.service.agent.AgentToolTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentExecutionRepository")
class AgentExecutionRepositoryTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private AgentExecutionRepository repository() {
        return new AgentExecutionRepository(jdbc, objectMapper);
    }

    @Test
    @DisplayName("save: INSERT with the tool-call trace serialized as JSONB")
    void saveSerializesTrace() {
        AgentExecution execution = new AgentExecution(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                "user-1", "do it", "completed",
                List.of(new AgentToolTrace("search", Map.of("q", "x"), "{\"r\":1}", false, true)),
                5, 7, 1, "done", null, Instant.now());

        repository().save(execution);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue())
                .contains("INSERT INTO ai_agent_execution")
                .contains("?::jsonb");
        String traceJson = Arrays.stream(args.getValue())
                .filter(a -> a instanceof String s && s.contains("\"name\""))
                .map(String::valueOf).findFirst().orElse("");
        assertThat(traceJson).contains("\"name\":\"search\"").contains("\"permitted\":true");
    }

    @Test
    @DisplayName("findByAgent: maps rows and deserializes the tool-call trace")
    void findByAgentMapsRows() throws Exception {
        UUID agentId = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString("tenant_id")).thenReturn(TENANT);
        when(rs.getObject("agent_id", UUID.class)).thenReturn(agentId);
        when(rs.getString("user_id")).thenReturn("user-1");
        when(rs.getString("input")).thenReturn("do it");
        when(rs.getString("status")).thenReturn("completed");
        when(rs.getString("tool_calls")).thenReturn(
                "[{\"name\":\"search\",\"input\":{\"q\":\"x\"},\"resultJson\":\"{}\",\"isError\":false,\"permitted\":true}]");
        when(rs.getInt("input_tokens")).thenReturn(5);
        when(rs.getInt("output_tokens")).thenReturn(7);
        when(rs.getInt("iterations")).thenReturn(1);
        when(rs.getString("final_text")).thenReturn("done");
        when(rs.getString("error")).thenReturn(null);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(TENANT), eq(agentId), eq(10)))
                .thenAnswer(inv -> {
                    RowMapper<AgentExecution> rm = inv.getArgument(1);
                    return List.of(rm.mapRow(rs, 0));
                });

        List<AgentExecution> result = repository().findByAgent(TENANT, agentId, 10);

        assertThat(result).hasSize(1);
        AgentExecution e = result.get(0);
        assertThat(e.status()).isEqualTo("completed");
        assertThat(e.toolCalls()).hasSize(1);
        assertThat(e.toolCalls().get(0).name()).isEqualTo("search");
        assertThat(e.toolCalls().get(0).permitted()).isTrue();
    }
}
