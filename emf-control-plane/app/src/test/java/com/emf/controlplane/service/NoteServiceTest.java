package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateNoteRequest;
import com.emf.controlplane.dto.UpdateNoteRequest;
import com.emf.controlplane.entity.Note;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.NoteRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NoteService}.
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    private NoteService noteService;

    private static final String TENANT_ID = "tenant-001";
    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String USERNAME = "test-user";

    @BeforeEach
    void setUp() {
        noteService = new NoteService(noteRepository);
        TenantContextHolder.set(TENANT_ID, "test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("listNotes")
    class ListNotes {

        @Test
        @DisplayName("should return notes for a record in reverse chronological order")
        void shouldReturnNotes() {
            // Given
            Note note1 = createNote("note-1", "First note");
            Note note2 = createNote("note-2", "Second note");
            when(noteRepository.findByCollectionIdAndRecordIdOrderByCreatedAtDesc(
                    COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(note2, note1));

            // When
            List<Note> result = noteService.listNotes(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("Second note");
            assertThat(result.get(1).getContent()).isEqualTo("First note");
            verify(noteRepository).findByCollectionIdAndRecordIdOrderByCreatedAtDesc(
                    COLLECTION_ID, RECORD_ID);
        }

        @Test
        @DisplayName("should return empty list when no notes exist")
        void shouldReturnEmptyList() {
            // Given
            when(noteRepository.findByCollectionIdAndRecordIdOrderByCreatedAtDesc(
                    COLLECTION_ID, RECORD_ID))
                    .thenReturn(Collections.emptyList());

            // When
            List<Note> result = noteService.listNotes(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createNote")
    class CreateNote {

        @Test
        @DisplayName("should create a note with correct fields")
        void shouldCreateNote() {
            // Given
            CreateNoteRequest request = new CreateNoteRequest();
            request.setContent("My new note");

            when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> {
                Note saved = invocation.getArgument(0);
                saved.setId("generated-id");
                return saved;
            });

            // When
            Note result = noteService.createNote(COLLECTION_ID, RECORD_ID, request, USERNAME);

            // Then
            ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
            verify(noteRepository).save(captor.capture());

            Note captured = captor.getValue();
            assertThat(captured.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captured.getCollectionId()).isEqualTo(COLLECTION_ID);
            assertThat(captured.getRecordId()).isEqualTo(RECORD_ID);
            assertThat(captured.getContent()).isEqualTo("My new note");
            assertThat(captured.getCreatedBy()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("should set createdBy to SYSTEM when username is null")
        void shouldSetSystemWhenUsernameNull() {
            // Given
            CreateNoteRequest request = new CreateNoteRequest();
            request.setContent("System note");

            when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            noteService.createNote(COLLECTION_ID, RECORD_ID, request, null);

            // Then
            ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
            verify(noteRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("should throw when no tenant context is set")
        void shouldThrowWhenNoTenantContext() {
            // Given
            TenantContextHolder.clear();
            CreateNoteRequest request = new CreateNoteRequest();
            request.setContent("Note without tenant");

            // When / Then
            assertThatThrownBy(() ->
                    noteService.createNote(COLLECTION_ID, RECORD_ID, request, USERNAME))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No tenant context");
        }
    }

    @Nested
    @DisplayName("updateNote")
    class UpdateNote {

        @Test
        @DisplayName("should update note content")
        void shouldUpdateNote() {
            // Given
            Note existing = createNote("note-1", "Old content");
            existing.setCreatedBy(USERNAME);
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UpdateNoteRequest request = new UpdateNoteRequest();
            request.setContent("Updated content");

            // When
            Note result = noteService.updateNote("note-1", request, USERNAME);

            // Then
            assertThat(result.getContent()).isEqualTo("Updated content");
            verify(noteRepository).save(existing);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when note not found")
        void shouldThrowWhenNoteNotFound() {
            // Given
            when(noteRepository.findById("nonexistent")).thenReturn(Optional.empty());

            UpdateNoteRequest request = new UpdateNoteRequest();
            request.setContent("Updated content");

            // When / Then
            assertThatThrownBy(() -> noteService.updateNote("nonexistent", request, USERNAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Note")
                    .hasMessageContaining("nonexistent");
        }
    }

    @Nested
    @DisplayName("deleteNote")
    class DeleteNote {

        @Test
        @DisplayName("should delete an existing note")
        void shouldDeleteNote() {
            // Given
            Note existing = createNote("note-1", "To be deleted");
            existing.setCreatedBy(USERNAME);
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));

            // When
            noteService.deleteNote("note-1", USERNAME);

            // Then
            verify(noteRepository).delete(existing);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when note not found")
        void shouldThrowWhenNoteNotFound() {
            // Given
            when(noteRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> noteService.deleteNote("nonexistent", USERNAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Note")
                    .hasMessageContaining("nonexistent");
        }
    }

    /**
     * Helper to create a Note with common fields set.
     */
    private Note createNote(String id, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTenantId(TENANT_ID);
        note.setCollectionId(COLLECTION_ID);
        note.setRecordId(RECORD_ID);
        note.setContent(content);
        note.setCreatedBy(USERNAME);
        return note;
    }
}
