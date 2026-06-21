package io.kelta.ai.repository;

import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.service.agent.AgentToolTrace;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JDBC repository for {@link AgentExecution} audit rows. Tenant-scoped on every query (explicit
 * {@code WHERE tenant_id = ?}, like the other kelta-ai repositories).
 */
@Repository
public class AgentExecutionRepository {

    private static final TypeReference<List<AgentToolTrace>> TRACE_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AgentExecutionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(AgentExecution execution) {
        jdbc.update("""
                        INSERT INTO ai_agent_execution (id, tenant_id, agent_id, user_id, input, status,
                            tool_calls, input_tokens, output_tokens, iterations, final_text, error, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """,
                execution.id(),
                execution.tenantId(),
                execution.agentId(),
                execution.userId(),
                execution.input(),
                execution.status(),
                toJson(execution.toolCalls()),
                execution.inputTokens(),
                execution.outputTokens(),
                execution.iterations(),
                execution.finalText(),
                execution.error(),
                Timestamp.from(execution.createdAt()));
    }

    public List<AgentExecution> findByAgent(String tenantId, UUID agentId, int limit) {
        return jdbc.query("""
                        SELECT * FROM ai_agent_execution
                        WHERE tenant_id = ? AND agent_id = ?
                        ORDER BY created_at DESC LIMIT ?
                        """,
                this::mapRow, tenantId, agentId, limit);
    }

    private AgentExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AgentExecution(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("agent_id", UUID.class),
                rs.getString("user_id"),
                rs.getString("input"),
                rs.getString("status"),
                fromJson(rs.getString("tool_calls")),
                rs.getInt("input_tokens"),
                rs.getInt("output_tokens"),
                rs.getInt("iterations"),
                rs.getString("final_text"),
                rs.getString("error"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(List<AgentToolTrace> traces) {
        try {
            return objectMapper.writeValueAsString(traces == null ? List.of() : traces);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize tool_calls", e);
        }
    }

    private List<AgentToolTrace> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, TRACE_LIST);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to deserialize tool_calls", e);
        }
    }
}
