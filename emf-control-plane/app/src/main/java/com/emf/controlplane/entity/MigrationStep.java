package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a single step within a migration run.
 * Each step tracks an individual operation with its status.
 */
@Entity
@Table(name = "migration_step")
public class MigrationStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "migration_run_id", nullable = false)
    private MigrationRun migrationRun;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public MigrationStep() {
        super();
    }

    public MigrationStep(Integer stepNumber, String operation) {
        super();
        this.stepNumber = stepNumber;
        this.operation = operation;
        this.status = "PENDING";
    }

    public MigrationRun getMigrationRun() {
        return migrationRun;
    }

    public void setMigrationRun(MigrationRun migrationRun) {
        this.migrationRun = migrationRun;
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

    @Override
    public String toString() {
        return "MigrationStep{" +
                "id='" + getId() + '\'' +
                ", stepNumber=" + stepNumber +
                ", operation='" + operation + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
