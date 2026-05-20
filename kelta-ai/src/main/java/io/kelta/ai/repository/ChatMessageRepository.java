package io.kelta.ai.repository;

import io.kelta.ai.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatMessageRepository {

    private static final TypeReference<List<Map<String, Object>>> BLOCK_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChatMessageRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(ChatMessage message) {
        String contentBlocksJson = serializeBlocks(message.contentBlocks());
        String legacyContent = message.legacyContent() != null ? message.legacyContent() : message.displayText();
        String legacyProposalJson = message.legacyProposalJson();
        if (legacyProposalJson == null) {
            legacyProposalJson = firstProposalJson(message.contentBlocks());
        }

        jdbc.update("""
                        INSERT INTO ai_message (id, tenant_id, conversation_id, role, content, proposal_json, content_blocks, tokens_input, tokens_output, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                        """,
                message.id(),
                message.tenantId(),
                message.conversationId(),
                message.role(),
                legacyContent,
                legacyProposalJson,
                contentBlocksJson,
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
        String containment = "[{\"proposalId\":\"" + proposalId + "\"}]";
        List<ChatMessage> results = jdbc.query("""
                        SELECT * FROM ai_message
                         WHERE tenant_id = ?
                           AND (content_blocks @> ?::jsonb OR proposal_json->>'id' = ?)
                         LIMIT 1
                        """,
                this::mapRow, tenantId, containment, proposalId.toString());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        String contentBlocksJson = rs.getString("content_blocks");
        List<Map<String, Object>> blocks = deserializeBlocks(contentBlocksJson);
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("role"),
                blocks,
                rs.getString("content"),
                rs.getString("proposal_json"),
                rs.getInt("tokens_input"),
                rs.getInt("tokens_output"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String serializeBlocks(List<Map<String, Object>> blocks) {
        if (blocks == null) return "[]";
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize content_blocks", e);
        }
    }

    private List<Map<String, Object>> deserializeBlocks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, BLOCK_LIST_TYPE);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize content_blocks", e);
        }
    }

    private String firstProposalJson(List<Map<String, Object>> blocks) {
        if (blocks == null) return null;
        for (Map<String, Object> b : blocks) {
            Object proposalId = b.get("proposalId");
            Object name = b.get("name");
            if (proposalId != null && "tool_use".equals(b.get("type"))
                    && name != null && String.valueOf(name).startsWith("propose_")) {
                try {
                    return objectMapper.writeValueAsString(Map.of(
                            "id", proposalId,
                            "type", inferTypeFromName(String.valueOf(name)),
                            "status", "pending",
                            "data", b.get("input")
                    ));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String inferTypeFromName(String toolName) {
        return switch (toolName) {
            case "propose_collection" -> "collection";
            case "propose_layout" -> "layout";
            case "propose_add_fields" -> "add_fields";
            case "propose_update_field" -> "update_field";
            case "propose_remove_field" -> "remove_field";
            case "propose_picklist" -> "picklist";
            default -> "unknown";
        };
    }
}
