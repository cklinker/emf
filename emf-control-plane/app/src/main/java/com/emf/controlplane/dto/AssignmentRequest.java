package com.emf.controlplane.dto;

/**
 * Request DTO for manually assigning a collection to a worker.
 */
public record AssignmentRequest(
    String collectionId,
    String tenantId
) {}
