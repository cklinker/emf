package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateNoteRequest;
import com.emf.controlplane.dto.UpdateNoteRequest;
import com.emf.controlplane.entity.Note;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.NoteRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing text notes attached to records.
 *
 * @since 1.0.0
 */
@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /**
     * List all notes for a specific record in reverse chronological order.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return list of notes
     */
    @Transactional(readOnly = true)
    public List<Note> listNotes(String collectionId, String recordId) {
        return noteRepository.findByCollectionIdAndRecordIdOrderByCreatedAtDesc(collectionId, recordId);
    }

    /**
     * Create a new note on a record.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @param request      the create request containing note content
     * @param username     the authenticated username
     * @return the created note
     */
    @Transactional
    public Note createNote(String collectionId, String recordId,
                           CreateNoteRequest request, String username) {
        String tenantId = TenantContextHolder.requireTenantId();

        log.info("Creating note for record {} in collection {} by user {}",
                recordId, collectionId, username);

        Note note = new Note();
        note.setTenantId(tenantId);
        note.setCollectionId(collectionId);
        note.setRecordId(recordId);
        note.setContent(request.getContent());
        note.setCreatedBy(username != null ? username : "SYSTEM");

        return noteRepository.save(note);
    }

    /**
     * Update an existing note. Only the creator can update their own notes.
     *
     * @param noteId   the note UUID
     * @param request  the update request containing new content
     * @param username the authenticated username
     * @return the updated note
     * @throws ResourceNotFoundException if the note is not found or user is not the creator
     */
    @Transactional
    public Note updateNote(String noteId, UpdateNoteRequest request, String username) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", noteId));

        note.setContent(request.getContent());
        return noteRepository.save(note);
    }

    /**
     * Delete a note. Only the creator can delete their own notes.
     *
     * @param noteId   the note UUID
     * @param username the authenticated username
     * @throws ResourceNotFoundException if the note is not found or user is not the creator
     */
    @Transactional
    public void deleteNote(String noteId, String username) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", noteId));

        log.info("Deleting note {} by user {}", noteId, username);
        noteRepository.delete(note);
    }
}
