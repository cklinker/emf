package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.kelta.worker.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Handles file attachment uploads via presigned PUT URLs.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client calls {@code POST /api/attachments/upload-url} with file metadata</li>
 *   <li>Server validates metadata, checks storage limits, creates an attachment record,
 *       and returns a presigned PUT URL</li>
 *   <li>Client uploads the file directly to S3 using the presigned URL</li>
 *   <li>Client calls {@code POST /api/attachments/{id}/finalize} to mark the upload complete</li>
 * </ol>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/attachments")
@ConditionalOnBean(S3StorageService.class)
public class AttachmentUploadController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentUploadController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final long BYTES_PER_GB = 1_073_741_824L;

    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "application/x-executable",
            "application/x-sharedlib",
            "application/x-msdos-program"
    );

    private static final String INSERT_ATTACHMENT = """
            INSERT INTO file_attachment (id, tenant_id, collection_id, record_id,
                file_name, file_size, content_type, storage_key, uploaded_by, created_at, updated_at)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

    private static final String UPDATE_ATTACHMENT_STORAGE_KEY = """
            UPDATE file_attachment SET storage_key = ?, uploaded_at = NOW(), updated_at = NOW()
            WHERE id = ?::uuid AND tenant_id = ?
            """;

    private static final String SELECT_ATTACHMENT = """
            SELECT id, tenant_id, collection_id, record_id, file_name, file_size,
                   content_type, storage_key, uploaded_by, uploaded_at, created_at
            FROM file_attachment WHERE id = ?::uuid AND tenant_id = ?
            """;

    private final S3StorageService storageService;
    private final GovernorLimitsRepository governorLimitsRepository;
    private final JdbcTemplate jdbcTemplate;

    public AttachmentUploadController(S3StorageService storageService,
                                       GovernorLimitsRepository governorLimitsRepository,
                                       JdbcTemplate jdbcTemplate) {
        this.storageService = storageService;
        this.governorLimitsRepository = governorLimitsRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Requests a presigned PUT URL for uploading a file attachment.
     *
     * <p>Validates file metadata, checks storage governor limits, creates
     * a pending attachment record, and returns the presigned URL.
     *
     * @param tenantId     the tenant ID from gateway header
     * @param userEmail    the authenticated user's email
     * @param body         request body with file metadata
     * @return presigned URL and attachment metadata
     */
    @PostMapping("/upload-url")
    public ResponseEntity<Map<String, Object>> requestUploadUrl(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody Map<String, Object> body) {

        String fileName = getStringRequired(body, "fileName");
        String contentType = getStringRequired(body, "contentType");
        Long fileSize = getLongRequired(body, "fileSize");
        String collectionId = getStringRequired(body, "collectionId");
        String recordId = getStringRequired(body, "recordId");

        if (fileName == null || contentType == null || fileSize == null
                || collectionId == null || recordId == null) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Missing required fields",
                            "Required: fileName, contentType, fileSize, collectionId, recordId"));
        }

        // Validate content type
        if (BLOCKED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Blocked content type",
                            "Content type '" + contentType + "' is not allowed"));
        }

        // Validate file size
        long maxFileSize = storageService.getMaxFileSize();
        if (fileSize <= 0 || fileSize > maxFileSize) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Invalid file size",
                            "File size must be between 1 and " + maxFileSize + " bytes"));
        }

        // Check storage governor limit
        long currentStorageBytes = governorLimitsRepository.sumStorageBytes(tenantId);
        long storageGbLimit = 10; // default
        try {
            Optional<Object> limitsOpt = governorLimitsRepository.findTenantLimits(tenantId);
            if (limitsOpt.isPresent() && limitsOpt.get() instanceof Map<?,?> limitsMap) {
                Object storageGbObj = limitsMap.get("storageGb");
                if (storageGbObj instanceof Number num) {
                    storageGbLimit = num.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read storage limits for tenant {}: {}", tenantId, e.getMessage());
        }

        long storageLimitBytes = storageGbLimit * BYTES_PER_GB;
        if (currentStorageBytes + fileSize > storageLimitBytes) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    JsonApiResponseBuilder.error("403", "Storage limit exceeded",
                            "Upload would exceed the " + storageGbLimit + " GB storage limit"));
        }

        // Generate storage key: tenantId/collectionId/recordId/attachmentId/fileName
        String attachmentId = UUID.randomUUID().toString();
        String sanitizedFileName = sanitizeFileName(fileName);
        String storageKey = tenantId + "/" + collectionId + "/" + recordId
                + "/" + attachmentId + "/" + sanitizedFileName;

        // Create pending attachment record (storageKey empty until finalized)
        try {
            jdbcTemplate.update(INSERT_ATTACHMENT,
                    attachmentId, tenantId, collectionId, recordId,
                    fileName, fileSize, contentType, "", userEmail);
        } catch (Exception e) {
            log.error("Failed to create attachment record for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Failed to create attachment record", e.getMessage()));
        }

        // Generate presigned PUT URL
        Duration expiry = storageService.getDefaultExpiry();
        String uploadUrl = storageService.getPresignedUploadUrl(storageKey, contentType, expiry);

        securityLog.info("security_event=UPLOAD_URL_GENERATED user={} tenant={} attachment={} file={}",
                userEmail, tenantId, attachmentId, sanitizedFileName);

        // Build response
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("attachmentId", attachmentId);
        attributes.put("uploadUrl", uploadUrl);
        attributes.put("storageKey", storageKey);
        attributes.put("expiresInSeconds", expiry.toSeconds());
        attributes.put("method", "PUT");
        attributes.put("headers", Map.of("Content-Type", contentType));

        return ResponseEntity.ok(JsonApiResponseBuilder.single("upload-urls", attachmentId, attributes));
    }

    /**
     * Finalizes an attachment upload by recording the storage key.
     *
     * <p>Called after the client has successfully uploaded the file to S3
     * using the presigned URL. Updates the attachment record with the
     * storage key and upload timestamp.
     *
     * @param id        the attachment ID
     * @param tenantId  the tenant ID from gateway header
     * @param userEmail the authenticated user's email
     * @return the finalized attachment record
     */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeUpload(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail) {

        // Look up the attachment
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_ATTACHMENT, id, tenantId);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> attachment = rows.get(0);
        String existingKey = (String) attachment.get("storage_key");

        if (existingKey != null && !existingKey.isBlank()) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Already finalized",
                            "This attachment has already been finalized"));
        }

        // Reconstruct the storage key
        String collectionId = (String) attachment.get("collection_id");
        String recordId = (String) attachment.get("record_id");
        String fileName = (String) attachment.get("file_name");
        String storageKey = tenantId + "/" + collectionId + "/" + recordId
                + "/" + id + "/" + sanitizeFileName(fileName);

        // Verify the object exists in S3
        if (!storageService.objectExists(storageKey)) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Upload not found",
                            "File has not been uploaded to storage yet"));
        }

        // Update the attachment record
        int updated = jdbcTemplate.update(UPDATE_ATTACHMENT_STORAGE_KEY, storageKey, id, tenantId);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        securityLog.info("security_event=UPLOAD_FINALIZED user={} tenant={} attachment={} storageKey={}",
                userEmail, tenantId, id, storageKey);

        // Return the finalized attachment
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("fileName", attachment.get("file_name"));
        attributes.put("fileSize", attachment.get("file_size"));
        attributes.put("contentType", attachment.get("content_type"));
        attributes.put("storageKey", storageKey);
        attributes.put("uploadedBy", attachment.get("uploaded_by"));
        attributes.put("uploadedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(JsonApiResponseBuilder.single("attachments", id, attributes));
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "upload";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String getStringRequired(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private static Long getLongRequired(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number num) {
            return num.longValue();
        }
        return null;
    }
}
