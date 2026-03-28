package io.kelta.ai.repository;

import io.kelta.ai.model.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Conversation conversation) {
        jdbc.update("""
                INSERT INTO ai_conversation (id, tenant_id, user_id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, updated_at = EXCLUDED.updated_at
                """,
                conversation.id(),
                conversation.tenantId(),
                conversation.userId(),
                conversation.title(),
                Timestamp.from(conversation.createdAt()),
                Timestamp.from(conversation.updatedAt()));
    }

    public Optional<Conversation> findById(UUID id, long tenantId) {
        List<Conversation> results = jdbc.query(
                "SELECT * FROM ai_conversation WHERE id = ? AND tenant_id = ?",
                this::mapRow, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Conversation> findByUser(long tenantId, String userId, int limit) {
        return jdbc.query(
                "SELECT * FROM ai_conversation WHERE tenant_id = ? AND user_id = ? ORDER BY updated_at DESC LIMIT ?",
                this::mapRow, tenantId, userId, limit);
    }

    public void updateTitle(UUID id, long tenantId, String title) {
        jdbc.update("UPDATE ai_conversation SET title = ?, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                title, id, tenantId);
    }

    public void updateTimestamp(UUID id, long tenantId) {
        jdbc.update("UPDATE ai_conversation SET updated_at = NOW() WHERE id = ? AND tenant_id = ?", id, tenantId);
    }

    private Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getObject("id", UUID.class),
                rs.getLong("tenant_id"),
                rs.getString("user_id"),
                rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
