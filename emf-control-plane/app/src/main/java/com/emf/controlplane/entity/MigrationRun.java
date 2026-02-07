package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an execution of schema migrations.
 * Tracks the migration from one collection version to another.
 */
@Entity
@Table(name = "migration_run")
public class MigrationRun extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "from_version", nullable = false)
    private Integer fromVersion;

    @Column(name = "to_version", nullable = false)
    private Integer toVersion;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @OneToMany(mappedBy = "migrationRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<MigrationStep> steps = new ArrayList<>();

    public MigrationRun() {
        super();
    }

    public MigrationRun(String collectionId, Integer fromVersion, Integer toVersion) {
        super();
        this.collectionId = collectionId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.status = "PENDING";
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public Integer getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(Integer fromVersion) {
        this.fromVersion = fromVersion;
    }

    public Integer getToVersion() {
        return toVersion;
    }

    public void setToVersion(Integer toVersion) {
        this.toVersion = toVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<MigrationStep> getSteps() {
        return steps;
    }

    public void setSteps(List<MigrationStep> steps) {
        this.steps = steps;
    }

    public void addStep(MigrationStep step) {
        steps.add(step);
        step.setMigrationRun(this);
    }

    public void removeStep(MigrationStep step) {
        steps.remove(step);
        step.setMigrationRun(null);
    }

    @Override
    public String toString() {
        return "MigrationRun{" +
                "id='" + getId() + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", fromVersion=" + fromVersion +
                ", toVersion=" + toVersion +
                ", status='" + status + '\'' +
                '}';
    }
}
