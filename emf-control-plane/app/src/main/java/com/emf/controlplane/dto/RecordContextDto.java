package com.emf.controlplane.dto;

import java.util.List;

/**
 * Combined response DTO for record notes and attachments.
 * Returned by the record-context endpoint to eliminate two separate API calls.
 *
 * @param notes       the notes for this record in reverse chronological order
 * @param attachments the file attachments for this record in reverse chronological order
 * @since 1.0.0
 */
public record RecordContextDto(
        List<NoteDto> notes,
        List<AttachmentDto> attachments
) {
}
