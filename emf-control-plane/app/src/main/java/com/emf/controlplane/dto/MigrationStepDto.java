package com.emf.controlplane.dto;

import java.time.Instant;

/**
 * DTO representing a single migration step.
 * Each step tracks an individual operation with its status.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.4: Track each migration step with success/failure status</li>
 * </ul>
 */
public class MigrationStepDto {

    private String id;
    private Integer stepNumber;
    private String operation;
    private String status;
    private String details;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public MigrationStepDto() {
    }

    public MigrationStepDto(String id, Integer stepNumber, String operation, String status) {
        this.id = id;
        this.stepNumber = stepNumber;
        this.operation = operation;
        this.status = status;
    }

    public MigrationStepDto(Integer stepNumber, String operation, String details) {
        this.stepNumber = stepNumber;
        this.operation = operation;
        this.details = details;
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(Integer stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    @Override
    public String toString() {
        return "MigrationStepDto{" +
                "stepNumber=" + stepNumber +
                ", operation='" + operation + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
