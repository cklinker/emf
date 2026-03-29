package io.kelta.ai.repository;

import io.kelta.ai.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbc;

    public ChatMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ChatMessage message) {
        jdbc.update("""
                INSERT INTO ai_message (id, tenant_id, conversation_id, role, content, proposal_json, tokens_input, tokens_output, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """,
                message.id(),
                message.tenantId(),
                message.conversationId(),
                message.role(),
                message.content(),
                message.proposalJson(),
                message.tokensInput(),
                message.tokensOutput(),
                Timestamp.from(message.createdAt()));
    }

    public List<ChatMessage> findByConversation(UUID conversationId, String tenantId) {
        return jdbc.query(
                "SELECT * FROM ai_message WHERE conversation_id = ? AND tenant_id = ? ORDER BY created_at ASC",
                this::mapRow, conversationId, tenantId);
    }

    public Optional<ChatMessage> findById(UUID id, String tenantId) {
        List<ChatMessage> results = jdbc.query(
                "SELECT * FROM ai_message WHERE id = ? AND tenant_id = ?",
                this::mapRow, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<ChatMessage> findByProposalId(UUID proposalId, String tenantId) {
        // Proposal ID is stored inside the proposal_json JSONB
        List<ChatMessage> results = jdbc.query(
                "SELECT * FROM ai_message WHERE tenant_id = ? AND proposal_json->>'id' = ? LIMIT 1",
                this::mapRow, tenantId, proposalId.toString());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("proposal_json"),
                rs.getInt("tokens_input"),
                rs.getInt("tokens_output"),
                rs.getTimestamp("created_at").toInstant());
    }
}
