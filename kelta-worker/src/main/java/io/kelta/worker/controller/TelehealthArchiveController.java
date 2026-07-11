package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.ChatService;
import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.SecurityAuditLogger;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import io.kelta.worker.service.telehealth.ArchiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encounter-record archive API (telehealth slice 7). Rides the authenticated
 * {@code /api/telehealth/**} static gateway route (which carries only the
 * blanket API_ACCESS check), so ALL authorization is enforced here — the chat
 * idiom: a PORTAL actor is confined to their own {@code portal_user_id};
 * retention settings and legal-hold toggles require {@code MANAGE_DATA}.
 *
 * <p>Reads run under the request's tenant-bound transaction (RLS-scoped); every
 * artifact download mints short-lived presigned URLs and is audited.
 */
@RestController
@RequestMapping("/api/telehealth")
public class TelehealthArchiveController {

    private static final Logger log = LoggerFactory.getLogger(TelehealthArchiveController.class);
    private static final String MANAGE_DATA = "MANAGE_DATA";
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final ArchiveService archiveService;
    private final JdbcTemplate jdbcTemplate;
    private final S3StorageService storageService;
    private final UserIdResolver userIdResolver;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;
    private final TenantQuotaResolver quotaResolver;
    private final io.kelta.worker.repository.GovernorLimitsRepository governorLimitsRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public TelehealthArchiveController(ArchiveService archiveService,
                                       JdbcTemplate jdbcTemplate,
                                       S3StorageService storageService,
                                       UserIdResolver userIdResolver,
                                       CerbosPermissionResolver permissionResolver,
                                       BootstrapRepository bootstrapRepository,
                                       TenantQuotaResolver quotaResolver,
                                       io.kelta.worker.repository.GovernorLimitsRepository governorLimitsRepository,
                                       tools.jackson.databind.ObjectMapper objectMapper) {
        this.archiveService = archiveService;
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
        this.userIdResolver = userIdResolver;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
        this.quotaResolver = quotaResolver;
        this.governorLimitsRepository = governorLimitsRepository;
        this.objectMapper = objectMapper;
    }

    public record ArchiveRequest(String sourceType, String sourceId) {}
    public record LegalHoldRequest(Boolean enabled) {}
    public record RetentionSettingsRequest(Integer archiveAfterDays, Integer retentionYears,
                                           Integer purgeLiveAfterDays) {}

    // ------------------------------------------------------------- Archives

    /** Manual archive-now (staff, member or MANAGE_CHAT). Idempotent. */
    @PostMapping("/archives")
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request,
                                                      @RequestBody ArchiveRequest body) {
        String tenantId = requireTenant();
        ChatService.ChatActor actor = actor(request, tenantId);
        if (actor.isPortal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Portal users cannot archive");
        }
        if (body == null || body.sourceType() == null || body.sourceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType and sourceId are required");
        }
        Map<String, Object> archive = switch (body.sourceType()) {
            case "CONVERSATION" -> archiveService.archiveConversation(tenantId, actor, body.sourceId());
            case "VIDEO_SESSION" -> archiveService.archiveVideoSession(tenantId, body.sourceId());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sourceType must be CONVERSATION or VIDEO_SESSION");
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(archive);
    }

    /** Metadata list. Staff sees the tenant; a PORTAL actor is forced to their own history. */
    @GetMapping("/archives")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request,
                                                    @RequestParam(required = false) String sourceType,
                                                    @RequestParam(required = false) String portalUserId,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        String tenantId = requireTenant();
        ChatService.ChatActor actor = actor(request, tenantId);

        StringBuilder sql = new StringBuilder(
                "SELECT id, source_type, source_id, appointment_id, portal_user_id, sha256, "
                        + "archived_at, archived_by, retention_until, legal_hold, purged_at "
                        + "FROM telehealth_archive WHERE tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (actor.isPortal()) {
            // Portal users only ever see their own history, regardless of params.
            sql.append("AND portal_user_id = ? ");
            args.add(actor.userId());
        } else if (portalUserId != null && !portalUserId.isBlank()) {
            sql.append("AND portal_user_id = ? ");
            args.add(portalUserId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append("AND source_type = ? ");
            args.add(sourceType);
        }
        if (from != null && !from.isBlank()) {
            sql.append("AND archived_at >= ? ");
            args.add(Timestamp.from(java.time.Instant.parse(from)));
        }
        if (to != null && !to.isBlank()) {
            sql.append("AND archived_at <= ? ");
            args.add(Timestamp.from(java.time.Instant.parse(to)));
        }
        sql.append("ORDER BY archived_at DESC NULLS LAST");

        List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(),
                (rs, i) -> metadataRow(rs), args.toArray());
        return ResponseEntity.ok(Map.of("data", rows));
    }

    /** Detail incl. presigned artifact download URLs. Audited; portal only their own. */
    @GetMapping("/archives/{id}")
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request, @PathVariable String id) {
        String tenantId = requireTenant();
        ChatService.ChatActor actor = actor(request, tenantId);

        Map<String, Object> archive = jdbcTemplate.queryForList(
                        "SELECT id, source_type, source_id, appointment_id, portal_user_id, "
                                + "artifact_attachment_ids, sha256, archived_at, archived_by, "
                                + "retention_until, legal_hold, purged_at FROM telehealth_archive "
                                + "WHERE id = ? AND tenant_id = ?", id, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive not found"));

        if (actor.isPortal() && !actor.userId().equals(str(archive.get("portal_user_id")))) {
            SecurityAuditLogger.log(SecurityAuditLogger.EventType.ARCHIVE_ACCESSED, actor.email(),
                    id, tenantId, "failure", "portal user denied — not owner");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your archive");
        }

        Map<String, Object> body = new LinkedHashMap<>(metadataMap(archive));
        body.put("artifacts", presignedArtifacts(tenantId, id));

        SecurityAuditLogger.log(SecurityAuditLogger.EventType.ARCHIVE_ACCESSED, actor.email(),
                id, tenantId, "success", null);
        return ResponseEntity.ok(body);
    }

    /** Legal hold on/off — MANAGE_DATA only. */
    @PostMapping("/archives/{id}/legal-hold")
    public ResponseEntity<Map<String, Object>> legalHold(HttpServletRequest request,
                                                         @PathVariable String id,
                                                         @RequestBody LegalHoldRequest body) {
        String tenantId = requireTenant();
        requireManageData(request);
        boolean enabled = body != null && Boolean.TRUE.equals(body.enabled());
        int updated = jdbcTemplate.update(
                "UPDATE telehealth_archive SET legal_hold = ?, updated_at = NOW() "
                        + "WHERE id = ? AND tenant_id = ?", enabled, id, tenantId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive not found");
        }
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.LEGAL_HOLD_CHANGED,
                actorEmail(request), id, tenantId, "success", "legalHold=" + enabled);
        return ResponseEntity.ok(Map.of("id", id, "legalHold", enabled));
    }

    // ------------------------------------------------------------- Settings

    @GetMapping("/retention-settings")
    public ResponseEntity<Map<String, Object>> getRetentionSettings(HttpServletRequest request) {
        String tenantId = requireTenant();
        actor(request, tenantId); // identity required
        Map<String, Object> quotas = quotaResolver.resolve(tenantId);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("archiveAfterDays", quotas.get(TenantTierQuotas.KEY_ARCHIVE_AFTER_DAYS));
        settings.put("retentionYears", quotas.get(TenantTierQuotas.KEY_RETENTION_YEARS));
        settings.put("purgeLiveAfterDays", quotas.get(TenantTierQuotas.KEY_PURGE_LIVE_AFTER_DAYS));
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/retention-settings")
    public ResponseEntity<Map<String, Object>> updateRetentionSettings(
            HttpServletRequest request, @RequestBody RetentionSettingsRequest body) {
        String tenantId = requireTenant();
        requireManageData(request);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        // Merge the three retention keys into the tenant's existing limits JSONB.
        Map<String, Object> limits = readTenantLimits(tenantId);
        putIfPresent(limits, TenantTierQuotas.KEY_ARCHIVE_AFTER_DAYS, body.archiveAfterDays(), 0, 3650);
        putIfPresent(limits, TenantTierQuotas.KEY_RETENTION_YEARS, body.retentionYears(), 1, 100);
        putIfPresent(limits, TenantTierQuotas.KEY_PURGE_LIVE_AFTER_DAYS, body.purgeLiveAfterDays(), 0, 3650);
        try {
            governorLimitsRepository.updateTenantLimits(tenantId, objectMapper.writeValueAsString(limits));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to persist retention settings");
        }
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.RETENTION_SETTINGS_CHANGED,
                actorEmail(request), tenantId, tenantId, "success",
                "archiveAfterDays=" + body.archiveAfterDays()
                        + " retentionYears=" + body.retentionYears()
                        + " purgeLiveAfterDays=" + body.purgeLiveAfterDays());
        return getRetentionSettings(request);
    }

    // ------------------------------------------------------------- Helpers

    private List<Map<String, Object>> presignedArtifacts(String tenantId, String archiveId) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, file_name, content_type, storage_key FROM file_attachment "
                        + "WHERE tenant_id = ? AND collection_id = 'telehealth-archives' AND record_id = ? "
                        + "ORDER BY created_at ASC, id ASC", tenantId, archiveId);
        for (Map<String, Object> row : rows) {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("id", row.get("id"));
            artifact.put("fileName", row.get("file_name"));
            artifact.put("contentType", row.get("content_type"));
            String storageKey = str(row.get("storage_key"));
            if (storageService.isEnabled() && storageKey != null && !storageKey.isBlank()) {
                try {
                    artifact.put("downloadUrl", storageService.getPresignedDownloadUrl(storageKey, DOWNLOAD_TTL));
                } catch (Exception e) {
                    log.warn("Failed to presign archive artifact {}: {}", storageKey, e.getMessage());
                }
            }
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private Map<String, Object> readTenantLimits(String tenantId) {
        Map<String, Object> merged = new LinkedHashMap<>();
        try {
            governorLimitsRepository.findTenantLimits(tenantId).ifPresent(limitsObj -> {
                Map<String, Object> parsed = parseLimits(limitsObj);
                merged.putAll(parsed);
            });
        } catch (Exception e) {
            log.warn("Failed to read tenant limits for {}, starting from empty overrides: {}",
                    tenantId, e.getMessage());
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLimits(Object limitsObj) {
        if (limitsObj == null) {
            return Map.of();
        }
        if (limitsObj instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        String limitsStr = limitsObj instanceof String s ? s : limitsObj.toString();
        if (limitsStr.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(limitsStr,
                    objectMapper.getTypeFactory().constructMapType(
                            java.util.HashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void putIfPresent(Map<String, Object> limits, String key, Integer value, int min, int max) {
        if (value == null) {
            return;
        }
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    key + " must be between " + min + " and " + max);
        }
        limits.put(key, value);
    }

    private Map<String, Object> metadataRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("sourceType", rs.getString("source_type"));
        row.put("sourceId", rs.getString("source_id"));
        row.put("appointmentId", rs.getString("appointment_id"));
        row.put("portalUserId", rs.getString("portal_user_id"));
        row.put("sha256", rs.getString("sha256"));
        row.put("archivedAt", ts(rs.getTimestamp("archived_at")));
        row.put("archivedBy", rs.getString("archived_by"));
        row.put("retentionUntil", ts(rs.getTimestamp("retention_until")));
        row.put("legalHold", rs.getBoolean("legal_hold"));
        row.put("purgedAt", ts(rs.getTimestamp("purged_at")));
        return row;
    }

    private Map<String, Object> metadataMap(Map<String, Object> archive) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", archive.get("id"));
        row.put("sourceType", archive.get("source_type"));
        row.put("sourceId", archive.get("source_id"));
        row.put("appointmentId", archive.get("appointment_id"));
        row.put("portalUserId", archive.get("portal_user_id"));
        row.put("sha256", archive.get("sha256"));
        row.put("archivedAt", instantString(archive.get("archived_at")));
        row.put("archivedBy", archive.get("archived_by"));
        row.put("retentionUntil", instantString(archive.get("retention_until")));
        row.put("legalHold", archive.get("legal_hold"));
        row.put("purgedAt", instantString(archive.get("purged_at")));
        return row;
    }

    private void requireManageData(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> MANAGE_DATA.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    MANAGE_DATA + " permission required");
        }
    }

    private ChatService.ChatActor actor(HttpServletRequest request, String tenantId) {
        String identifier = request.getHeader("X-User-Id");
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        String userId = userIdResolver.resolve(identifier, tenantId);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unresolvable identity");
        }
        String userType = request.getHeader("X-User-Type");
        return new ChatService.ChatActor(userId, identifier,
                userType == null || userType.isBlank() ? "INTERNAL" : userType);
    }

    private String actorEmail(HttpServletRequest request) {
        String identifier = request.getHeader("X-User-Id");
        return identifier == null || identifier.isBlank() ? "unknown" : identifier;
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private static String ts(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static String instantString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        return String.valueOf(value);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
