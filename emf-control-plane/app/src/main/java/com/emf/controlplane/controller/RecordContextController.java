package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AttachmentDto;
import com.emf.controlplane.dto.NoteDto;
import com.emf.controlplane.dto.RecordContextDto;
import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.entity.Note;
import com.emf.controlplane.service.AttachmentService;
import com.emf.controlplane.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that returns combined notes and attachments for a record
 * in a single API call. This eliminates the need for two separate HTTP
 * round trips, reducing total latency by ~50%.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/control/record-context")
@Tag(name = "Record Context", description = "Combined notes and attachments for a record")
public class RecordContextController {

    private static final Logger log = LoggerFactory.getLogger(RecordContextController.class);

    private final NoteService noteService;
    private final AttachmentService attachmentService;

    public RecordContextController(NoteService noteService, AttachmentService attachmentService) {
        this.noteService = noteService;
        this.attachmentService = attachmentService;
    }

    /**
     * Get combined notes and attachments for a record in a single call.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return combined notes and attachments
     */
    @GetMapping("/{collectionId}/{recordId}")
    @PreAuthorize("@securityService.hasObjectPermission(#root, #collectionId, 'READ')")
    @Operation(summary = "Get record context",
            description = "Returns both notes and attachments for a record in a single response")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved record context")
    public ResponseEntity<RecordContextDto> getRecordContext(
            @PathVariable String collectionId,
            @PathVariable String recordId) {
        log.debug("REST request to get record context for collection={}, record={}", collectionId, recordId);

        List<Note> notes = noteService.listNotes(collectionId, recordId);
        List<NoteDto> noteDtos = notes.stream()
                .map(NoteDto::fromEntity)
                .toList();

        List<FileAttachment> attachments = attachmentService.listAttachments(collectionId, recordId);
        List<AttachmentDto> attachmentDtos = attachments.stream()
                .map(att -> {
                    String downloadUrl = attachmentService.getDownloadUrlForKey(att.getStorageKey());
                    return AttachmentDto.fromEntity(att, downloadUrl);
                })
                .toList();

        return ResponseEntity.ok(new RecordContextDto(noteDtos, attachmentDtos));
    }
}
