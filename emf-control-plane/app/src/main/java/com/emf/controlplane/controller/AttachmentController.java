package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AttachmentDto;
import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.service.AttachmentService;
import com.emf.controlplane.service.S3StorageService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing file attachments with S3 storage.
 *
 * <p>Endpoint paths match the UI AttachmentsSection component expectations:
 * <ul>
 *   <li>GET /control/attachments/{collectionId}/{recordId} - list attachments</li>
 *   <li>POST /control/attachments/{collectionId}/{recordId} - upload attachment</li>
 *   <li>GET /control/attachments/download/{attachmentId} - download attachment (redirect)</li>
 *   <li>DELETE /control/attachments/{attachmentId} - delete attachment</li>
 * </ul>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/control/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;
    private final S3StorageService storageService;

    public AttachmentController(AttachmentService attachmentService,
                                @Nullable S3StorageService storageService) {
        this.attachmentService = attachmentService;
        this.storageService = storageService;
    }

    /**
     * List all attachments for a record. When S3 is available, each attachment
     * includes a presigned download URL.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return list of attachment metadata in reverse chronological order
     */
    @GetMapping("/{collectionId}/{recordId}")
    @PreAuthorize("@securityService.hasObjectPermission(#root, #collectionId, 'READ')")
    public ResponseEntity<List<AttachmentDto>> listAttachments(
            @PathVariable String collectionId,
            @PathVariable String recordId) {
        List<FileAttachment> attachments = attachmentService.listAttachments(collectionId, recordId);
        List<AttachmentDto> dtos = attachments.stream()
                .map(att -> {
                    String downloadUrl = attachmentService.getDownloadUrlForKey(att.getStorageKey());
                    return AttachmentDto.fromEntity(att, downloadUrl);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Upload a file attachment for a record.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @param file         the multipart file
     * @return the created attachment metadata with download URL
     */
    @PostMapping("/{collectionId}/{recordId}")
    @PreAuthorize("@securityService.hasObjectPermission(#root, #collectionId, 'EDIT')")
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @PathVariable String collectionId,
            @PathVariable String recordId,
            @RequestParam("file") MultipartFile file) {

        if (storageService == null) {
            log.warn("Upload attachment called but S3 storage is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // Validate file is not empty
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate file size against max
        if (file.getSize() > storageService.getMaxFileSize()) {
            log.warn("Upload rejected: file size {} exceeds max {}",
                    file.getSize(), storageService.getMaxFileSize());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        String username = getCurrentUsername();
        String tenantId = TenantContextHolder.requireTenantId();
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unnamed";
        String contentType = file.getContentType() != null
                ? file.getContentType() : "application/octet-stream";

        // Generate storage key: {tenantId}/{collectionId}/{recordId}/{uuid}/{originalFilename}
        String storageKey = String.format("%s/%s/%s/%s/%s",
                tenantId, collectionId, recordId,
                UUID.randomUUID(), originalFilename);

        try {
            // Upload to S3
            storageService.upload(storageKey, file.getInputStream(), file.getSize(), contentType);

            // Create metadata record
            FileAttachment saved = attachmentService.createAttachment(
                    collectionId, recordId, originalFilename,
                    file.getSize(), contentType, storageKey, username);

            // Generate download URL
            String downloadUrl = attachmentService.getDownloadUrlForKey(storageKey);
            AttachmentDto dto = AttachmentDto.fromEntity(saved, downloadUrl);

            log.info("Uploaded attachment {} (file: {}, size: {}) by {}",
                    saved.getId(), originalFilename, file.getSize(), username);

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            log.error("Failed to upload attachment for collection={}, record={}: {}",
                    collectionId, recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download an attachment by redirecting to a presigned S3 URL.
     *
     * @param attachmentId the attachment UUID
     * @return 302 redirect to presigned URL, or 503 if S3 not available
     */
    @GetMapping("/download/{attachmentId}")
    @PreAuthorize("@securityService.hasAttachmentPermission(#root, #attachmentId, 'READ')")
    public ResponseEntity<Void> downloadAttachment(@PathVariable String attachmentId) {
        String downloadUrl = attachmentService.getDownloadUrl(attachmentId);

        if (downloadUrl == null) {
            log.warn("Download requested for attachment {} but no download URL available", attachmentId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(downloadUrl))
                .build();
    }

    /**
     * Delete an attachment.
     *
     * @param attachmentId the attachment UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@securityService.hasAttachmentPermission(#root, #attachmentId, 'EDIT')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable String attachmentId) {
        String username = getCurrentUsername();
        log.info("REST request to delete attachment {} by {}", attachmentId, username);

        attachmentService.deleteAttachment(attachmentId, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract the authenticated username from the SecurityContext.
     *
     * @return the username, or "ANONYMOUS" if not authenticated
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }
}
