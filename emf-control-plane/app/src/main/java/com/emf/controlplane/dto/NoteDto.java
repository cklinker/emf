package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Note;

import java.time.Instant;

/**
 * Response DTO for a note.
 * Matches the UI NotesSection expected shape: { id, content, createdBy, createdAt, updatedAt }.
 *
 * @since 1.0.0
 */
public class NoteDto {

    private String id;
    private String content;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public NoteDto() {
    }

    /**
     * Creates a DTO from a Note entity.
     *
     * @param entity the Note entity
     * @return the DTO, or null if entity is null
     */
    public static NoteDto fromEntity(Note entity) {
        if (entity == null) {
            return null;
        }
        NoteDto dto = new NoteDto();
        dto.setId(entity.getId());
        dto.setContent(entity.getContent());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
