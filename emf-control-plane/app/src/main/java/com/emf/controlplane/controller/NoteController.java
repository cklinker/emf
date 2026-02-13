package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateNoteRequest;
import com.emf.controlplane.dto.NoteDto;
import com.emf.controlplane.dto.UpdateNoteRequest;
import com.emf.controlplane.entity.Note;
import com.emf.controlplane.service.NoteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing notes attached to records.
 *
 * <p>Endpoint paths match the UI NotesSection component expectations:
 * <ul>
 *   <li>GET /control/notes/{collectionId}/{recordId} - list notes</li>
 *   <li>POST /control/notes/{collectionId}/{recordId} - create note</li>
 *   <li>PUT /control/notes/{noteId} - update note</li>
 *   <li>DELETE /control/notes/{noteId} - delete note</li>
 * </ul>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/control/notes")
public class NoteController {

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * List all notes for a record.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return list of notes in reverse chronological order
     */
    @GetMapping("/{collectionId}/{recordId}")
    public ResponseEntity<List<NoteDto>> listNotes(
            @PathVariable String collectionId,
            @PathVariable String recordId) {
        List<Note> notes = noteService.listNotes(collectionId, recordId);
        List<NoteDto> dtos = notes.stream()
                .map(NoteDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Create a new note on a record.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @param request      the create request
     * @return the created note
     */
    @PostMapping("/{collectionId}/{recordId}")
    public ResponseEntity<NoteDto> createNote(
            @PathVariable String collectionId,
            @PathVariable String recordId,
            @Valid @RequestBody CreateNoteRequest request) {
        String username = getCurrentUsername();
        log.info("REST request to create note for record {}/{} by {}",
                collectionId, recordId, username);

        Note created = noteService.createNote(collectionId, recordId, request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteDto.fromEntity(created));
    }

    /**
     * Update an existing note.
     *
     * @param noteId  the note UUID
     * @param request the update request
     * @return the updated note
     */
    @PutMapping("/{noteId}")
    public ResponseEntity<NoteDto> updateNote(
            @PathVariable String noteId,
            @Valid @RequestBody UpdateNoteRequest request) {
        String username = getCurrentUsername();
        log.info("REST request to update note {} by {}", noteId, username);

        Note updated = noteService.updateNote(noteId, request, username);
        return ResponseEntity.ok(NoteDto.fromEntity(updated));
    }

    /**
     * Delete a note.
     *
     * @param noteId the note UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable String noteId) {
        String username = getCurrentUsername();
        log.info("REST request to delete note {} by {}", noteId, username);

        noteService.deleteNote(noteId, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract the authenticated username from the SecurityContext.
     * Follows the same pattern as {@code SetupAuditService.getCurrentUserId()}.
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
