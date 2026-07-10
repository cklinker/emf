package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Human-to-human chat (telehealth slice 2, specs/telehealth/2-chat-backend.md).
 *
 * <p><b>Writes go through {@link QueryEngine}</b> so the chat BeforeSaveHooks
 * (sender/participant enforcement, NATS chat events) and the generic
 * record.changed pipeline (flows, audit) fire on every mutation, whichever
 * path a write takes. <b>Reads are plain JDBC</b> under the request's
 * tenant-bound transaction (RLS-scoped). Read receipts intentionally bypass
 * the engine — a per-focus lastReadAt bump must not spam record.changed.
 *
 * <p>Authorization model (enforced here, in-controller — the generic JSON:API
 * routes carry no object grants for these collections, so only
 * VIEW_ALL_DATA/MODIFY_ALL_DATA admins can touch them there):
 * <ul>
 *   <li>{@code mine} views + send/read: conversation participants only.</li>
 *   <li>{@code queue} view + claim: INTERNAL users.</li>
 *   <li>{@code all} view + queue management: {@code MANAGE_CHAT}.</li>
 *   <li>PORTAL users can start a conversation (their entry point) and act
 *       only within conversations they participate in.</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_PAGE_SIZE = 100;

    public record ChatActor(String userId, String email, String userType) {
        public boolean isPortal() {
            return "PORTAL".equals(userType);
        }
    }

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final ParticipantShareSupport participantShareSupport;

    public ChatService(QueryEngine queryEngine,
                       CollectionRegistry collectionRegistry,
                       JdbcTemplate jdbcTemplate,
                       ParticipantShareSupport participantShareSupport) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.participantShareSupport = participantShareSupport;
    }

    // ------------------------------------------------------------- Conversations

    public Map<String, Object> startConversation(String tenantId, ChatActor actor,
                                                 String queueId, String subject,
                                                 String contextRecordId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        if (queueId != null && !queueId.isBlank()) {
            requireActiveQueue(queueId);
            data.put("queueId", queueId);
        }
        if (subject != null && !subject.isBlank()) {
            data.put("subject", subject.length() > 200 ? subject.substring(0, 200) : subject);
        }
        if (contextRecordId != null && !contextRecordId.isBlank()) {
            data.put("contextRecordId", contextRecordId);
        }
        data.put("status", "OPEN");
        data.put("origin", actor.isPortal() ? "PORTAL" : "INTERNAL");

        Map<String, Object> conversation = queryEngine.create(definition("chat-conversations"), data);
        String conversationId = String.valueOf(conversation.get("id"));

        addParticipant(tenantId, conversationId, actor.userId(),
                actor.isPortal() ? "PORTAL" : "AGENT");

        SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_CONVERSATION_OPENED,
                actor.email(), conversationId, tenantId, "success", null);
        return conversation;
    }

    /** Inbox lists. view ∈ mine|queue|all; authz enforced by the controller. */
    public List<Map<String, Object>> listConversations(String tenantId, ChatActor actor,
                                                       String view, String queueId,
                                                       String status, int page, int size) {
        int limit = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int offset = Math.max(0, page) * limit;

        // my_last_read_at feeds the client's unread computation (slice 3):
        // unread ⇔ last_message_at > my_last_read_at (or never read).
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.queue_id, c.subject, c.status, c.origin, c.assigned_to,
                       c.context_record_id, c.last_message_at, c.closed_at, c.created_at,
                       (SELECT p2.last_read_at FROM chat_participant p2
                        WHERE p2.conversation_id = c.id AND p2.user_id = ?) AS my_last_read_at
                FROM chat_conversation c
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(actor.userId());
        sql.append("WHERE c.tenant_id = ? ");
        args.add(tenantId);

        switch (view) {
            case "mine" -> {
                sql.append("AND EXISTS (SELECT 1 FROM chat_participant p "
                        + "WHERE p.conversation_id = c.id AND p.user_id = ?) ");
                args.add(actor.userId());
            }
            case "queue" -> sql.append("AND c.status = 'OPEN' AND c.assigned_to IS NULL ");
            case "all" -> { /* no extra filter */ }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "view must be mine, queue, or all");
        }
        if (queueId != null && !queueId.isBlank()) {
            sql.append("AND c.queue_id = ? ");
            args.add(queueId);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND c.status = ? ");
            args.add(status);
        }
        sql.append("ORDER BY c.last_message_at DESC NULLS LAST, c.created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("queueId", rs.getString("queue_id"));
            row.put("subject", rs.getString("subject"));
            row.put("status", rs.getString("status"));
            row.put("origin", rs.getString("origin"));
            row.put("assignedTo", rs.getString("assigned_to"));
            row.put("contextRecordId", rs.getString("context_record_id"));
            row.put("lastMessageAt", ts(rs.getTimestamp("last_message_at")));
            row.put("closedAt", ts(rs.getTimestamp("closed_at")));
            row.put("createdAt", ts(rs.getTimestamp("created_at")));
            row.put("myLastReadAt", ts(rs.getTimestamp("my_last_read_at")));
            return row;
        }, args.toArray());
    }

    public Map<String, Object> getConversation(String tenantId, ChatActor actor, String conversationId) {
        requireMember(tenantId, conversationId, actor);
        return loadConversation(conversationId);
    }

    /** INTERNAL users claim an OPEN conversation (or MANAGE_CHAT reassigns). */
    public Map<String, Object> assign(String tenantId, ChatActor actor, String conversationId,
                                      String assigneeId, boolean canManage) {
        if (actor.isPortal()) {
            deny(tenantId, actor, conversationId, "portal user cannot assign");
        }
        Map<String, Object> conversation = loadConversation(conversationId);
        String status = String.valueOf(conversation.get("status"));
        if ("CLOSED".equals(status) || "ARCHIVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation is " + status);
        }
        String target = (assigneeId == null || assigneeId.isBlank()) ? actor.userId() : assigneeId;
        if (!target.equals(actor.userId()) && !canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "MANAGE_CHAT permission required to assign to another user");
        }

        Map<String, Object> updated = queryEngine.update(definition("chat-conversations"),
                        conversationId, Map.of("status", "ASSIGNED", "assignedTo", target))
                .orElseThrow(() -> notFound());
        addParticipant(tenantId, conversationId, target, "AGENT");
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_CONVERSATION_ASSIGNED,
                actor.email(), conversationId, tenantId, "success", "assignee=" + target);
        return updated;
    }

    public Map<String, Object> close(String tenantId, ChatActor actor, String conversationId,
                                     boolean canManage) {
        if (!canManage) {
            requireMember(tenantId, conversationId, actor);
        }
        Map<String, Object> updated = queryEngine.update(definition("chat-conversations"),
                        conversationId,
                        Map.of("status", "CLOSED", "closedAt", Instant.now().toString()))
                .orElseThrow(() -> notFound());
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_CONVERSATION_CLOSED,
                actor.email(), conversationId, tenantId, "success", null);
        return updated;
    }

    // ------------------------------------------------------------- Messages

    public Map<String, Object> sendMessage(String tenantId, ChatActor actor, String conversationId,
                                           String body, String kind) {
        requireMember(tenantId, conversationId, actor);
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("conversationId", conversationId);
        // Sender identity comes from the gateway-verified actor — a
        // body-supplied senderId is never honored (approvals precedent).
        data.put("senderId", actor.userId());
        data.put("senderType", actor.isPortal() ? "PORTAL" : "INTERNAL");
        data.put("kind", kind == null || kind.isBlank() ? "TEXT" : kind);
        data.put("body", body);
        data.put("sentAt", Instant.now().toString());
        return queryEngine.create(definition("chat-messages"), data);
    }

    public List<Map<String, Object>> getMessages(String tenantId, ChatActor actor,
                                                 String conversationId, String afterMessageId,
                                                 int size) {
        requireMember(tenantId, conversationId, actor);
        int limit = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<Object> args = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.sender_id, m.sender_type, m.kind, m.body, m.sent_at
                FROM chat_message m
                WHERE m.tenant_id = ? AND m.conversation_id = ?
                """);
        args.add(tenantId);
        args.add(conversationId);
        if (afterMessageId != null && !afterMessageId.isBlank()) {
            sql.append("AND m.sent_at > (SELECT sent_at FROM chat_message WHERE id = ?) ");
            args.add(afterMessageId);
        }
        sql.append("ORDER BY m.sent_at ASC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("senderId", rs.getString("sender_id"));
            row.put("senderType", rs.getString("sender_type"));
            row.put("kind", rs.getString("kind"));
            row.put("body", rs.getString("body"));
            row.put("sentAt", ts(rs.getTimestamp("sent_at")));
            return row;
        }, args.toArray());
    }

    /** Read receipt — direct JDBC on purpose: no record.changed noise per focus. */
    public void markRead(String tenantId, ChatActor actor, String conversationId) {
        int updated = jdbcTemplate.update(
                "UPDATE chat_participant SET last_read_at = ?, updated_at = NOW() "
                        + "WHERE tenant_id = ? AND conversation_id = ? AND user_id = ?",
                Timestamp.from(Instant.now()), tenantId, conversationId, actor.userId());
        if (updated == 0) {
            deny(tenantId, actor, conversationId, "read receipt from non-participant");
        }
    }

    // ------------------------------------------------------------- Membership

    /** True when the identifier (platform_user id OR email) is a participant. */
    public boolean isMember(String tenantId, String conversationId, String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM chat_participant cp
                JOIN platform_user pu ON pu.id = cp.user_id
                WHERE cp.tenant_id = ? AND cp.conversation_id = ?
                  AND (pu.id = ? OR pu.email = ?)
                """,
                Integer.class, tenantId, conversationId, userIdentifier, userIdentifier);
        return count != null && count > 0;
    }

    void requireMember(String tenantId, String conversationId, ChatActor actor) {
        if (!isMember(tenantId, conversationId, actor.userId())) {
            deny(tenantId, actor, conversationId, "not a participant");
        }
    }

    private void deny(String tenantId, ChatActor actor, String conversationId, String reason) {
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.CHAT_ACCESS_DENIED,
                actor.email(), conversationId, tenantId, "failure", reason);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a conversation participant");
    }

    // ------------------------------------------------------------- Internals

    private void addParticipant(String tenantId, String conversationId, String userId, String role) {
        if (isMember(tenantId, conversationId, userId)) {
            return;
        }
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("tenantId", tenantId);
        participant.put("conversationId", conversationId);
        participant.put("userId", userId);
        participant.put("role", role);
        participant.put("joinedAt", Instant.now().toString());
        queryEngine.create(definition("chat-participants"), participant);

        // Portal participants get a record_share on the conversation so future
        // surfaces using the share-widening path (timeline, exports) see it.
        if ("PORTAL".equals(role)) {
            participantShareSupport.grant("chat-conversations", conversationId, userId, "READ");
        }
    }

    private void requireActiveQueue(String queueId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_queue WHERE id = ? AND active = true",
                Integer.class, queueId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or inactive queue");
        }
    }

    private Map<String, Object> loadConversation(String conversationId) {
        return queryEngine.getById(definition("chat-conversations"), conversationId)
                .orElseThrow(this::notFound);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
    }

    private CollectionDefinition definition(String name) {
        CollectionDefinition definition = collectionRegistry.get(name);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    name + " collection not registered");
        }
        return definition;
    }

    private static String ts(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    /** Stable participant-row id helper for tests. */
    static String newId() {
        return UUID.randomUUID().toString();
    }
}
