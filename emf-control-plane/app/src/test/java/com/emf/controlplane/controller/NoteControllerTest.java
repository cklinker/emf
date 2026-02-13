package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateNoteRequest;
import com.emf.controlplane.dto.NoteDto;
import com.emf.controlplane.dto.UpdateNoteRequest;
import com.emf.controlplane.entity.Note;
import com.emf.controlplane.service.NoteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NoteController}.
 */
@ExtendWith(MockitoExtension.class)
class NoteControllerTest {

    @Mock
    private NoteService noteService;

    private NoteController noteController;

    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String NOTE_ID = "note-001";
    private static final String USERNAME = "test-user";

    @BeforeEach
    void setUp() {
        noteController = new NoteController(noteService);

        // Set up SecurityContext with a test user
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(
                new TestingAuthenticationToken(USERNAME, null));
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /{collectionId}/{recordId} - listNotes")
    class ListNotes {

        @Test
        @DisplayName("should return 200 with list of notes")
        void shouldReturnNotesWithOkStatus() {
            // Given
            Note note1 = createNote("note-1", "First note");
            Note note2 = createNote("note-2", "Second note");
            when(noteService.listNotes(COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(note1, note2));

            // When
            ResponseEntity<List<NoteDto>> response =
                    noteController.listNotes(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getId()).isEqualTo("note-1");
            assertThat(response.getBody().get(1).getId()).isEqualTo("note-2");
            verify(noteService).listNotes(COLLECTION_ID, RECORD_ID);
        }

        @Test
        @DisplayName("should return 200 with empty list when no notes exist")
        void shouldReturnEmptyList() {
            // Given
            when(noteService.listNotes(COLLECTION_ID, RECORD_ID))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<NoteDto>> response =
                    noteController.listNotes(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /{collectionId}/{recordId} - createNote")
    class CreateNote {

        @Test
        @DisplayName("should return 201 with created note")
        void shouldReturnCreatedNote() {
            // Given
            CreateNoteRequest request = new CreateNoteRequest();
            request.setContent("New note content");

            Note created = createNote(NOTE_ID, "New note content");
            when(noteService.createNote(eq(COLLECTION_ID), eq(RECORD_ID),
                    any(CreateNoteRequest.class), eq(USERNAME)))
                    .thenReturn(created);

            // When
            ResponseEntity<NoteDto> response =
                    noteController.createNote(COLLECTION_ID, RECORD_ID, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(NOTE_ID);
            assertThat(response.getBody().getContent()).isEqualTo("New note content");
            verify(noteService).createNote(eq(COLLECTION_ID), eq(RECORD_ID),
                    eq(request), eq(USERNAME));
        }
    }

    @Nested
    @DisplayName("PUT /{noteId} - updateNote")
    class UpdateNote {

        @Test
        @DisplayName("should return 200 with updated note")
        void shouldReturnUpdatedNote() {
            // Given
            UpdateNoteRequest request = new UpdateNoteRequest();
            request.setContent("Updated content");

            Note updated = createNote(NOTE_ID, "Updated content");
            when(noteService.updateNote(eq(NOTE_ID), any(UpdateNoteRequest.class), eq(USERNAME)))
                    .thenReturn(updated);

            // When
            ResponseEntity<NoteDto> response =
                    noteController.updateNote(NOTE_ID, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).isEqualTo("Updated content");
            verify(noteService).updateNote(eq(NOTE_ID), eq(request), eq(USERNAME));
        }
    }

    @Nested
    @DisplayName("DELETE /{noteId} - deleteNote")
    class DeleteNote {

        @Test
        @DisplayName("should return 204 No Content")
        void shouldReturnNoContent() {
            // When
            ResponseEntity<Void> response = noteController.deleteNote(NOTE_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(noteService).deleteNote(NOTE_ID, USERNAME);
        }
    }

    /**
     * Helper to create a Note with common fields set.
     */
    private Note createNote(String id, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTenantId("tenant-001");
        note.setCollectionId(COLLECTION_ID);
        note.setRecordId(RECORD_ID);
        note.setContent(content);
        note.setCreatedBy(USERNAME);
        return note;
    }
}
