package com.emf.controlplane.dto;

import com.emf.controlplane.entity.CollectionAssignment;

import java.time.Instant;

/**
 * DTO that enriches a {@link CollectionAssignment} with the collection's
 * name and display name so the UI can show human-readable labels instead
 * of opaque UUIDs.
 */
public record WorkerAssignmentDto(
        String id,
        String workerId,
        String collectionId,
        String collectionName,
        String collectionDisplayName,
        String tenantId,
        String status,
        Instant assignedAt,
        Instant readyAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Creates a DTO from a {@link CollectionAssignment} entity and
     * the resolved collection name / display name.
     *
     * @param assignment          the assignment entity
     * @param collectionName      the collection's internal name (e.g. "product")
     * @param collectionDisplayName the collection's display name (e.g. "Products")
     * @return a fully populated DTO
     */
    public static WorkerAssignmentDto from(
            CollectionAssignment assignment,
            String collectionName,
            String collectionDisplayName) {
        return new WorkerAssignmentDto(
                assignment.getId(),
                assignment.getWorkerId(),
                assignment.getCollectionId(),
                collectionName,
                collectionDisplayName,
                assignment.getTenantId(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getReadyAt(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}
