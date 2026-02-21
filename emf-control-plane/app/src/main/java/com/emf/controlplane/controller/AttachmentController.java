package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AttachmentDto;
import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.service.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing file attachment metadata.
 *
 * <p>Endpoint paths match the UI AttachmentsSection component expectations:
 * <ul>
 *   <li>GET /control/attachments/{collectionId}/{recordId} - list attachments</li>
 *   <li>DELETE /control/attachments/{attachmentId} - delete attachment</li>
 *   <li>POST /control/attachments/{collectionId}/{recordId} - upload (not yet implemented)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/control/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * List all attachments for a record.
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
                .map(AttachmentDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Delete an attachment.
     *
     * @param attachmentId the attachment UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable String attachmentId) {
        String username = getCurrentUsername();
        log.info("REST request to delete attachment {} by {}", attachmentId, username);

        attachmentService.deleteAttachment(attachmentId, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Placeholder for file upload. Returns 501 Not Implemented
     * until S3 integration is complete.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return 501 Not Implemented
     */
    @PostMapping("/{collectionId}/{recordId}")
    public ResponseEntity<String> uploadAttachment(
            @PathVariable String collectionId,
            @PathVariable String recordId) {
        log.warn("Upload attachment endpoint called but not yet implemented "
                + "(collection={}, record={})", collectionId, recordId);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("File upload not yet implemented. S3 integration is pending.");
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
