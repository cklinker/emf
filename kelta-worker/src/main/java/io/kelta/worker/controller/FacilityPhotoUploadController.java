package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.SpotopenedMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Presigned-upload access for spotopened's {@code facility-photos} collection.
 *
 * <p>Single step, unlike {@code AttachmentUploadController}'s upload-url +
 * finalize dance: this controller only ever mints a presigned PUT URL (see
 * {@link SpotopenedMediaStorageService}'s javadoc for why it's a separate
 * service). The client PUTs bytes to storage, then creates the metadata row
 * itself via the ordinary generic route ({@code POST /api/facility-photos}),
 * which is independently Cerbos-gated and guarded by {@code PhotoGuardHook}
 * -- there is nothing here to "finalize" because nothing here writes to a
 * database.
 *
 * <p>Not a registered collection route ({@code /api/facility-photo-uploads}
 * has no {@code RouteRegistry} entry), so {@code RouteAuthorizationFilter}
 * only requires the caller have {@code API_ACCESS} -- the same bar
 * {@code /api/attachments/upload-url} sits behind. spotopened's Guest
 * profile already has {@code API_ACCESS} (emf#1368), so an anonymous
 * caller can request an upload URL exactly like a signed-in member; nothing
 * additional to grant.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/facility-photo-uploads")
public class FacilityPhotoUploadController {

    private static final Logger log = LoggerFactory.getLogger(FacilityPhotoUploadController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "application/x-executable",
            "application/x-sharedlib",
            "application/x-msdos-program"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPE_PREFIXES = Set.of("image/");

    private final SpotopenedMediaStorageService storageService;

    public FacilityPhotoUploadController(SpotopenedMediaStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> requestUploadUrl(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody Map<String, Object> body) {

        if (!storageService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    JsonApiResponseBuilder.error("503", "Storage unavailable",
                            "spotopened-media storage is not enabled on this server"));
        }

        String fileName = getStringRequired(body, "fileName");
        String contentType = getStringRequired(body, "contentType");
        Long fileSize = getLongRequired(body, "fileSize");

        if (fileName == null || contentType == null || fileSize == null) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Missing required fields",
                            "Required: fileName, contentType, fileSize"));
        }

        if (BLOCKED_CONTENT_TYPES.contains(contentType)
                || ALLOWED_CONTENT_TYPE_PREFIXES.stream().noneMatch(contentType::startsWith)) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Invalid content type",
                            "Only image uploads are accepted"));
        }

        long maxFileSize = storageService.getMaxFileSize();
        if (fileSize <= 0 || fileSize > maxFileSize) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Invalid file size",
                            "File size must be between 1 and " + maxFileSize + " bytes"));
        }

        String storageKey = tenantId + "/facility-photos/" + UUID.randomUUID()
                + "/" + sanitizeFileName(fileName);

        String uploadUrl;
        try {
            uploadUrl = storageService.getPresignedUploadUrl(storageKey, contentType);
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for tenant {}: {}",
                    tenantId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Failed to generate upload URL", e.getMessage()));
        }

        securityLog.info("security_event=FACILITY_PHOTO_UPLOAD_URL_GENERATED user={} tenant={} "
                + "storageKey={}", userEmail, tenantId, storageKey);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("uploadUrl", uploadUrl);
        attributes.put("storageKey", storageKey);
        attributes.put("expiresInSeconds", storageService.getDefaultExpiry().toSeconds());
        attributes.put("method", "PUT");
        attributes.put("headers", Map.of("Content-Type", contentType));

        return ResponseEntity.ok(JsonApiResponseBuilder.single("facility-photo-uploads", storageKey, attributes));
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
