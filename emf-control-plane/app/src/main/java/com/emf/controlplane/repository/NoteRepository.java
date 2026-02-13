package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Note} entity operations.
 *
 * @since 1.0.0
 */
@Repository
public interface NoteRepository extends JpaRepository<Note, String> {

    /**
     * Find all notes for a specific record, ordered by creation date descending.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return notes in reverse chronological order
     */
    List<Note> findByCollectionIdAndRecordIdOrderByCreatedAtDesc(String collectionId, String recordId);
}
