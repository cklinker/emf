package io.kelta.ai.repository;

import io.kelta.ai.model.AgentDefinition;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for {@link AgentDefinition} rows in {@code ai_agent}. Every query is scoped by
 * {@code tenant_id} (kelta-ai connects as the table owner, so explicit filtering — not RLS — is the
 * isolation boundary, matching {@link ConversationRepository}).
 */
@Repository
public class AgentDefinitionRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AgentDefinitionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Inserts or updates the agent (keyed by id). May throw {@code DuplicateKeyException} on a name clash. */
    public void save(AgentDefinition agent) {
        jdbc.update("""
                        INSERT INTO ai_agent (id, tenant_id, name, description, system_prompt, model,
                            max_tokens, allowed_tools, monthly_token_budget, enabled, created_by,
                            updated_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            name = EXCLUDED.name,
                            description = EXCLUDED.description,
                            system_prompt = EXCLUDED.system_prompt,
                            model = EXCLUDED.model,
                            max_tokens = EXCLUDED.max_tokens,
                            allowed_tools = EXCLUDED.allowed_tools,
                            monthly_token_budget = EXCLUDED.monthly_token_budget,
                            enabled = EXCLUDED.enabled,
                            updated_by = EXCLUDED.updated_by,
                            updated_at = EXCLUDED.updated_at
                        """,
                agent.id(),
                agent.tenantId(),
                agent.name(),
                agent.description(),
                agent.systemPrompt(),
                agent.model(),
                agent.maxTokens(),
                toJson(agent.allowedTools()),
                agent.monthlyTokenBudget(),
                agent.enabled(),
                agent.createdBy(),
                agent.updatedBy(),
                Timestamp.from(agent.createdAt()),
                Timestamp.from(agent.updatedAt()));
    }

    public Optional<AgentDefinition> findById(UUID id, String tenantId) {
        List<AgentDefinition> results = jdbc.query(
                "SELECT * FROM ai_agent WHERE id = ? AND tenant_id = ?",
                this::mapRow, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<AgentDefinition> findByName(String tenantId, String name) {
        List<AgentDefinition> results = jdbc.query(
                "SELECT * FROM ai_agent WHERE tenant_id = ? AND name = ?",
                this::mapRow, tenantId, name);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<AgentDefinition> findByTenant(String tenantId) {
        return jdbc.query(
                "SELECT * FROM ai_agent WHERE tenant_id = ? ORDER BY name ASC",
                this::mapRow, tenantId);
    }

    /** Deletes the agent; returns true if a row was removed. */
    public boolean deleteById(UUID id, String tenantId) {
        return jdbc.update("DELETE FROM ai_agent WHERE id = ? AND tenant_id = ?", id, tenantId) > 0;
    }

    private AgentDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AgentDefinition(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("system_prompt"),
                rs.getString("model"),
                (Integer) rs.getObject("max_tokens"),
                fromJson(rs.getString("allowed_tools")),
                (Long) rs.getObject("monthly_token_budget"),
                rs.getBoolean("enabled"),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String toJson(List<String> tools) {
        try {
            return objectMapper.writeValueAsString(tools == null ? List.of() : tools);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize allowed_tools", e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to deserialize allowed_tools", e);
        }
    }
}
