package com.emf.worker.model;

/**
 * Model for parsing Kafka assignment events.
 *
 * <p>Represents a collection assignment change event sent by the control plane.
 * The {@code changeType} indicates whether a collection has been assigned or unassigned
 * from a worker.
 *
 * @param workerId the target worker ID
 * @param collectionId the collection being assigned or unassigned
 * @param workerBaseUrl the base URL of the target worker
 * @param collectionName the human-readable collection name
 * @param changeType the type of change: "CREATED" (assign) or "DELETED" (unassign)
 */
public record AssignmentEvent(
    String workerId,
    String collectionId,
    String workerBaseUrl,
    String collectionName,
    String changeType
) {}
