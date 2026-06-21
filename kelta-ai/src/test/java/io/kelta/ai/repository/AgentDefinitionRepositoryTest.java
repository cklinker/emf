package io.kelta.ai.repository;

import io.kelta.ai.model.AgentDefinition;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentDefinitionRepository")
class AgentDefinitionRepositoryTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private AgentDefinitionRepository repository() {
        return new AgentDefinitionRepository(jdbc, objectMapper);
    }

    @Test
    @DisplayName("save: INSERT … ON CONFLICT with allowed_tools serialized as a JSON array (?::jsonb)")
    void saveSerializesTools() {
        AgentDefinition agent = AgentDefinition.create(TENANT, "Bot", "d", "prompt", "claude-sonnet-4-6",
                2048, List.of("search", "get_record"), 500_000L, true, "user-1");

        repository().save(agent);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue())
                .contains("INSERT INTO ai_agent")
                .contains("?::jsonb")
                .contains("ON CONFLICT");
        assertThat(args.getValue()).contains("[\"search\",\"get_record\"]");
    }

    @Test
    @DisplayName("findById: maps the row and deserializes allowed_tools")
    void findByIdMapsRow() throws Exception {
        UUID id = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("tenant_id")).thenReturn(TENANT);
        when(rs.getString("name")).thenReturn("Bot");
        when(rs.getString("description")).thenReturn("d");
        when(rs.getString("system_prompt")).thenReturn("prompt");
        when(rs.getString("model")).thenReturn("claude-sonnet-4-6");
        when(rs.getObject("max_tokens")).thenReturn(2048);
        when(rs.getString("allowed_tools")).thenReturn("[\"search\",\"get_record\"]");
        when(rs.getObject("monthly_token_budget")).thenReturn(500_000L);
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getString("created_by")).thenReturn("user-1");
        when(rs.getString("updated_by")).thenReturn("user-1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(id), eq(TENANT)))
                .thenAnswer(inv -> {
                    RowMapper<AgentDefinition> rm = inv.getArgument(1);
                    return List.of(rm.mapRow(rs, 0));
                });

        Optional<AgentDefinition> result = repository().findById(id, TENANT);

        assertThat(result).isPresent();
        AgentDefinition a = result.get();
        assertThat(a.id()).isEqualTo(id);
        assertThat(a.name()).isEqualTo("Bot");
        assertThat(a.maxTokens()).isEqualTo(2048);
        assertThat(a.allowedTools()).containsExactly("search", "get_record");
        assertThat(a.monthlyTokenBudget()).isEqualTo(500_000L);
        assertThat(a.enabled()).isTrue();
    }

    @Test
    @DisplayName("findById: null allowed_tools deserializes to an empty list")
    void findByIdNullTools() throws Exception {
        UUID id = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("tenant_id")).thenReturn(TENANT);
        when(rs.getString("name")).thenReturn("Bot");
        when(rs.getString("description")).thenReturn(null);
        when(rs.getString("system_prompt")).thenReturn("prompt");
        when(rs.getString("model")).thenReturn(null);
        when(rs.getObject("max_tokens")).thenReturn(null);
        when(rs.getString("allowed_tools")).thenReturn(null);
        when(rs.getObject("monthly_token_budget")).thenReturn(null);
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getString("created_by")).thenReturn(null);
        when(rs.getString("updated_by")).thenReturn(null);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));

        when(jdbc.query(anyString(), any(RowMapper.class), eq(id), eq(TENANT)))
                .thenAnswer(inv -> {
                    RowMapper<AgentDefinition> rm = inv.getArgument(1);
                    return List.of(rm.mapRow(rs, 0));
                });

        Optional<AgentDefinition> result = repository().findById(id, TENANT);

        assertThat(result).isPresent();
        assertThat(result.get().allowedTools()).isEmpty();
    }

    @Test
    @DisplayName("deleteById: true when a row is removed, false otherwise")
    void deleteById() {
        UUID id = UUID.randomUUID();
        when(jdbc.update(contains("DELETE FROM ai_agent"), eq(id), eq(TENANT))).thenReturn(1);
        assertThat(repository().deleteById(id, TENANT)).isTrue();

        when(jdbc.update(contains("DELETE FROM ai_agent"), eq(id), eq(TENANT))).thenReturn(0);
        assertThat(repository().deleteById(id, TENANT)).isFalse();
    }
}
