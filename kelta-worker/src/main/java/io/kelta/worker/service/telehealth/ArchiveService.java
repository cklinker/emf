package io.kelta.worker.service.telehealth;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.ParticipantShareSupport;
import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.SecurityAuditLogger;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import io.kelta.worker.util.PdfTableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Encounter-record archival (telehealth slice 7,
 * {@code specs/telehealth/7-archival-retention.md}). Turns a CLOSED chat
 * conversation or an ENDED video session into an immutable, retrievable
 * artifact: a canonical JSON transcript/summary (SHA-256 pinned for tamper
 * evidence) plus a human-readable PDF render, both stored in S3 as
 * {@code file_attachment} rows OWNED by the archive row.
 *
 * <p><b>Archive-then-purge, never delete-first.</b> The archive is the durable
 * copy; live tables are only trimmed afterward by the retention sweeps. When a
 * conversation is archived its message attachments are RE-PARENTED to the
 * archive row ({@code record_id = <archiveId>}) so a later live-message purge
 * cannot delete what the transcript manifest references, and deleting the
 * archive later cascades them via {@code AttachmentCleanupHook}.
 *
 * <p><b>Idempotent.</b> One archive row per {@code (sourceType, sourceId)}
 * (unique index); a second request returns the existing row and never
 * re-archives.
 *
 * <p><b>Determinism.</b> The canonical JSON is emitted by
 * {@link #toCanonicalJson} with alphabetically sorted object keys via a
 * self-contained serializer, so the same encounter always hashes to the same
 * SHA-256 regardless of Jackson version or map iteration order.
 */
@Service
public class ArchiveService {

    /** Bump when the artifact shape changes; renders must stay back-readable. */
    static final int SCHEMA_VERSION = 1;
    private static final String COLLECTION = "telehealth-archives";
    private static final String SOURCE_CONVERSATION = "CONVERSATION";
    private static final String SOURCE_VIDEO = "VIDEO_SESSION";
    private static final String SYSTEM_ACTOR = "system";

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    private final JdbcTemplate jdbcTemplate;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final S3StorageService storageService;
    private final ParticipantShareSupport participantShareSupport;
    private final TenantQuotaResolver quotaResolver;

    public ArchiveService(JdbcTemplate jdbcTemplate,
                          QueryEngine queryEngine,
                          CollectionRegistry collectionRegistry,
                          S3StorageService storageService,
                          ParticipantShareSupport participantShareSupport,
                          TenantQuotaResolver quotaResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.storageService = storageService;
        this.participantShareSupport = participantShareSupport;
        this.quotaResolver = quotaResolver;
    }

    // ------------------------------------------------------------- Conversation

    /**
     * Archives a CLOSED (or CLOSED-eligible) conversation. Idempotent: returns
     * the existing archive row when one already exists. On first archival the
     * conversation is transitioned to {@code ARCHIVED} and its message
     * attachments are re-parented to the archive row.
     */
    public Map<String, Object> archiveConversation(String tenantId, ChatService.ChatActor actor,
                                                    String conversationId) {
        Map<String, Object> existing = findExisting(tenantId, SOURCE_CONVERSATION, conversationId);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> conversation = jdbcTemplate.queryForList(
                        "SELECT id, subject, status, assigned_to, last_message_at, closed_at, created_at "
                                + "FROM chat_conversation WHERE id = ? AND tenant_id = ?",
                        conversationId, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        List<Map<String, Object>> participants = jdbcTemplate.queryForList(
                "SELECT user_id, role, joined_at FROM chat_participant "
                        + "WHERE tenant_id = ? AND conversation_id = ? ORDER BY joined_at ASC, user_id ASC",
                tenantId, conversationId);
        List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                "SELECT id, sender_id, sender_type, kind, body, sent_at FROM chat_message "
                        + "WHERE tenant_id = ? AND conversation_id = ? ORDER BY sent_at ASC, id ASC",
                tenantId, conversationId);

        List<String> messageIds = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            messageIds.add(String.valueOf(message.get("id")));
        }
        List<Map<String, Object>> attachments = loadAttachments(tenantId, messageIds);

        String portalUserId = firstPortalParticipant(participants);
        Instant archivedAt = Instant.now();

        // Canonical artifact (stable key order → reproducible sha256).
        Map<String, Object> statusHistory = new LinkedHashMap<>();
        statusHistory.put("closedAt", instantString(conversation.get("closed_at")));
        statusHistory.put("archivedAt", archivedAt.toString());

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schemaVersion", SCHEMA_VERSION);
        canonical.put("sourceType", SOURCE_CONVERSATION);
        canonical.put("sourceId", conversationId);
        canonical.put("subject", conversation.get("subject"));
        canonical.put("participants", canonicalParticipants(participants));
        canonical.put("messages", canonicalMessages(messages));
        canonical.put("attachments", canonicalAttachmentManifest(attachments));
        canonical.put("statusHistory", statusHistory);

        String archiveId = UUID.randomUUID().toString();
        byte[] jsonBytes = toCanonicalJson(canonical).getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(jsonBytes);
        byte[] pdfBytes = renderConversationPdf(conversation, messages);

        List<String> artifactIds = storeArtifacts(tenantId, archiveId, "conversation-" + conversationId,
                jsonBytes, pdfBytes, actor);

        // Re-parent message attachments onto the archive row (pin): protects them
        // from a later live-message purge and cascades on archive delete.
        reParentAttachments(tenantId, messageIds, archiveId);

        Map<String, Object> row = insertArchive(tenantId, SOURCE_CONVERSATION, conversationId,
                null, portalUserId, artifactIds, sha256, archivedAt, actorId(actor));

        // Conversation → ARCHIVED (read-only; the ChatMessageHook rejects new writes).
        queryEngine.update(conversationDefinition(), conversationId, Map.of("status", "ARCHIVED"));

        grantPortalShare(archiveId, portalUserId);
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.ARCHIVE_CREATED, actorId(actor),
                archiveId, tenantId, "success", "source=CONVERSATION sourceId=" + conversationId);
        return row;
    }

    // ------------------------------------------------------------- Video session

    /**
     * Archives an ENDED video session under a system actor (called from the
     * LiveKit webhook path, which is tenant-less — the caller supplies the
     * tenant and this method establishes the tenant scope). Idempotent.
     */
    public Map<String, Object> archiveVideoSession(String tenantId, String sessionId) {
        return TenantContext.callWithTenant(tenantId, () -> doArchiveVideoSession(tenantId, sessionId));
    }

    private Map<String, Object> doArchiveVideoSession(String tenantId, String sessionId) {
        Map<String, Object> existing = findExisting(tenantId, SOURCE_VIDEO, sessionId);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> session = jdbcTemplate.queryForList(
                        "SELECT id, appointment_id, conversation_id, room_name, status, started_at, "
                                + "ended_at, duration_seconds, recording_consent, recording_key "
                                + "FROM video_session WHERE id = ? AND tenant_id = ?",
                        sessionId, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        String appointmentId = str(session.get("appointment_id"));
        String portalUserId = portalUserForSession(tenantId, appointmentId,
                str(session.get("conversation_id")));

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schemaVersion", SCHEMA_VERSION);
        canonical.put("sourceType", SOURCE_VIDEO);
        canonical.put("sourceId", sessionId);
        canonical.put("appointmentId", appointmentId);
        canonical.put("conversationId", str(session.get("conversation_id")));
        canonical.put("roomName", str(session.get("room_name")));
        canonical.put("status", str(session.get("status")));
        canonical.put("startedAt", instantString(session.get("started_at")));
        canonical.put("endedAt", instantString(session.get("ended_at")));
        canonical.put("durationSeconds", session.get("duration_seconds"));
        canonical.put("recordingConsent", session.get("recording_consent"));
        canonical.put("recordingKey", str(session.get("recording_key")));

        Instant archivedAt = Instant.now();
        String archiveId = UUID.randomUUID().toString();
        byte[] jsonBytes = toCanonicalJson(canonical).getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(jsonBytes);
        byte[] pdfBytes = renderVideoPdf(canonical);

        List<String> artifactIds = storeArtifacts(tenantId, archiveId, "session-" + sessionId,
                jsonBytes, pdfBytes, null);

        Map<String, Object> row = insertArchive(tenantId, SOURCE_VIDEO, sessionId,
                appointmentId, portalUserId, artifactIds, sha256, archivedAt, SYSTEM_ACTOR);

        grantPortalShare(archiveId, portalUserId);
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.ARCHIVE_CREATED, SYSTEM_ACTOR,
                archiveId, tenantId, "success", "source=VIDEO_SESSION sourceId=" + sessionId);
        return row;
    }

    // ------------------------------------------------------------- Canonical JSON

    private List<Object> canonicalParticipants(List<Map<String, Object>> participants) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> participant : participants) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", str(participant.get("user_id")));
            entry.put("role", str(participant.get("role")));
            entry.put("joinedAt", instantString(participant.get("joined_at")));
            out.add(entry);
        }
        return out;
    }

    // Package-private + static: pure mapping, exercised directly by tests to
    // assert reproducible transcript serialization.
    static List<Object> canonicalMessages(List<Map<String, Object>> messages) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", str(message.get("id")));
            entry.put("senderId", str(message.get("sender_id")));
            entry.put("senderType", str(message.get("sender_type")));
            entry.put("kind", str(message.get("kind")));
            entry.put("body", str(message.get("body")));
            entry.put("sentAt", instantString(message.get("sent_at")));
            out.add(entry);
        }
        return out;
    }

    private List<Object> canonicalAttachmentManifest(List<Map<String, Object>> attachments) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> attachment : attachments) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", str(attachment.get("id")));
            entry.put("fileName", str(attachment.get("file_name")));
            entry.put("storageKey", str(attachment.get("storage_key")));
            entry.put("contentType", str(attachment.get("content_type")));
            out.add(entry);
        }
        return out;
    }

    /**
     * Deterministic JSON serializer: object keys sorted alphabetically, minimal
     * whitespace, RFC-8259 string escaping. Self-contained (no Jackson config
     * dependency) so the byte output — and therefore the SHA-256 — is stable
     * across runtimes for identical input.
     */
    static String toCanonicalJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            // Sort keys for a canonical, reproducible ordering.
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(':');
                writeJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object element : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(sb, element);
            }
            sb.append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ------------------------------------------------------------- PDF render

    private byte[] renderConversationPdf(Map<String, Object> conversation,
                                         List<Map<String, Object>> messages) {
        String subject = str(conversation.get("subject"));
        String title = "Chat transcript" + (subject == null ? "" : " — " + subject);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfTableWriter writer = new PdfTableWriter(out, title,
                List.of("Timestamp", "Sender", "Message"))) {
            for (Map<String, Object> message : messages) {
                writer.writeRow(List.of(
                        nullToEmpty(instantString(message.get("sent_at"))),
                        nullToEmpty(str(message.get("sender_type"))),
                        nullToEmpty(str(message.get("body")))));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render transcript PDF", e);
        }
        return out.toByteArray();
    }

    private byte[] renderVideoPdf(Map<String, Object> canonical) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfTableWriter writer = new PdfTableWriter(out, "Video visit summary",
                List.of("Field", "Value"))) {
            for (String key : List.of("sourceId", "appointmentId", "roomName", "status",
                    "startedAt", "endedAt", "durationSeconds", "recordingConsent", "recordingKey")) {
                Object v = canonical.get(key);
                writer.writeRow(List.of(key, v == null ? "" : String.valueOf(v)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render video summary PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------- Storage

    /**
     * Stores the JSON + PDF artifacts to S3 (when enabled) and writes
     * {@code file_attachment} rows OWNED by the archive record. Returns the new
     * attachment ids (order: [json, pdf]).
     */
    private List<String> storeArtifacts(String tenantId, String archiveId, String basename,
                                        byte[] jsonBytes, byte[] pdfBytes, ChatService.ChatActor actor) {
        String uploadedBy = actor != null ? actor.email() : SYSTEM_ACTOR;
        String jsonId = writeArtifact(tenantId, archiveId, basename + ".json",
                "application/json", jsonBytes, uploadedBy);
        String pdfId = writeArtifact(tenantId, archiveId, basename + ".pdf",
                "application/pdf", pdfBytes, uploadedBy);
        return List.of(jsonId, pdfId);
    }

    private String writeArtifact(String tenantId, String archiveId, String fileName,
                                 String contentType, byte[] data, String uploadedBy) {
        String attachmentId = UUID.randomUUID().toString();
        String storageKey = tenantId + "/" + COLLECTION + "/" + archiveId
                + "/" + attachmentId + "/" + fileName;
        if (storageService.isEnabled()) {
            storageService.uploadObject(storageKey, data, contentType);
        } else {
            log.warn("S3 disabled — archive artifact {} recorded without object upload", storageKey);
        }
        jdbcTemplate.update(
                "INSERT INTO file_attachment (id, tenant_id, collection_id, record_id, "
                        + "file_name, file_size, content_type, storage_key, uploaded_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                attachmentId, tenantId, COLLECTION, archiveId,
                fileName, (long) data.length, contentType, storageKey, uploadedBy);
        return attachmentId;
    }

    private void reParentAttachments(String tenantId, List<String> messageIds, String archiveId) {
        if (messageIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(COLLECTION);
        args.add(archiveId);
        args.add(tenantId);
        args.addAll(messageIds);
        int moved = jdbcTemplate.update(
                "UPDATE file_attachment SET collection_id = ?, record_id = ?, updated_at = NOW() "
                        + "WHERE tenant_id = ? AND record_id IN (" + placeholders + ")",
                args.toArray());
        if (moved > 0) {
            log.info("Re-parented {} message attachment(s) to archive {}", moved, archiveId);
        }
    }

    private List<Map<String, Object>> loadAttachments(String tenantId, List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(messageIds);
        return jdbcTemplate.queryForList(
                "SELECT id, file_name, storage_key, content_type FROM file_attachment "
                        + "WHERE tenant_id = ? AND record_id IN (" + placeholders + ") "
                        + "ORDER BY created_at ASC, id ASC",
                args.toArray());
    }

    // ------------------------------------------------------------- Persistence

    private Map<String, Object> insertArchive(String tenantId, String sourceType, String sourceId,
                                              String appointmentId, String portalUserId,
                                              List<String> artifactIds, String sha256,
                                              Instant archivedAt, String archivedBy) {
        int retentionYears = intSetting(tenantId, TenantTierQuotas.KEY_RETENTION_YEARS, 7);
        Instant retentionUntil = archivedAt.atZone(java.time.ZoneOffset.UTC)
                .plusYears(retentionYears).toInstant();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("sourceType", sourceType);
        data.put("sourceId", sourceId);
        if (appointmentId != null) {
            data.put("appointmentId", appointmentId);
        }
        if (portalUserId != null) {
            data.put("portalUserId", portalUserId);
        }
        data.put("artifactAttachmentIds", toCanonicalJson(artifactIds));
        data.put("sha256", sha256);
        data.put("archivedAt", archivedAt.toString());
        data.put("archivedBy", archivedBy);
        data.put("retentionUntil", retentionUntil.toString());
        data.put("legalHold", false);
        try {
            return queryEngine.create(archiveDefinition(), data);
        } catch (RuntimeException e) {
            // Lost the idempotency race — the winning row is returned.
            Map<String, Object> winner = findExisting(tenantId, sourceType, sourceId);
            if (winner != null) {
                return winner;
            }
            throw e;
        }
    }

    private Map<String, Object> findExisting(String tenantId, String sourceType, String sourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, source_type, source_id, appointment_id, portal_user_id, "
                        + "artifact_attachment_ids, sha256, archived_at, archived_by, retention_until, "
                        + "legal_hold, purged_at FROM telehealth_archive "
                        + "WHERE tenant_id = ? AND source_type = ? AND source_id = ?",
                tenantId, sourceType, sourceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------- Helpers

    private void grantPortalShare(String archiveId, String portalUserId) {
        if (portalUserId != null && !portalUserId.isBlank()) {
            participantShareSupport.grant(COLLECTION, archiveId, portalUserId, "READ");
        }
    }

    private String firstPortalParticipant(List<Map<String, Object>> participants) {
        for (Map<String, Object> participant : participants) {
            if ("PORTAL".equals(str(participant.get("role")))) {
                return str(participant.get("user_id"));
            }
        }
        return null;
    }

    private String portalUserForSession(String tenantId, String appointmentId, String conversationId) {
        if (appointmentId != null) {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT portal_user_id FROM telehealth_appointment WHERE id = ? AND tenant_id = ?",
                    String.class, appointmentId, tenantId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        if (conversationId != null) {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT user_id FROM chat_participant WHERE tenant_id = ? AND conversation_id = ? "
                            + "AND role = 'PORTAL' ORDER BY joined_at ASC LIMIT 1",
                    String.class, tenantId, conversationId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return null;
    }

    private int intSetting(String tenantId, String key, int fallback) {
        Object value = quotaResolver.resolve(tenantId).get(key);
        if (value instanceof Number num) {
            int i = num.intValue();
            return i == Integer.MAX_VALUE ? fallback : i;
        }
        return fallback;
    }

    private static String actorId(ChatService.ChatActor actor) {
        return actor == null ? SYSTEM_ACTOR : actor.userId();
    }

    private CollectionDefinition archiveDefinition() {
        return definition(COLLECTION);
    }

    private CollectionDefinition conversationDefinition() {
        return definition("chat-conversations");
    }

    private CollectionDefinition definition(String name) {
        CollectionDefinition definition = collectionRegistry.get(name);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    name + " collection not registered");
        }
        return definition;
    }

    private static String instantString(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (timestamp instanceof Instant instant) {
            return instant.toString();
        }
        return String.valueOf(timestamp);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
