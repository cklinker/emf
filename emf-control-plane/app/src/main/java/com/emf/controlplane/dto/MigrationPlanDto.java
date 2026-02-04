package com.emf.controlplane.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a migration plan.
 * Contains the steps required to migrate from the current schema to the target schema.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.1: Generate a plan showing the steps required to migrate from current to target schema</li>
 * </ul>
 */
public class MigrationPlanDto {

    private String id;
    private String collectionId;
    private String collectionName;
    private Integer fromVersion;
    private Integer toVersion;
    private String status;
    private List<MigrationStepDto> steps;
    private Instant createdAt;

    public MigrationPlanDto() {
        this.steps = new ArrayList<>();
    }

    public MigrationPlanDto(String id, String collectionId, String collectionName, 
                           Integer fromVersion, Integer toVersion) {
        this.id = id;
        this.collectionId = collectionId;
        this.collectionName = collectionName;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.status = "PLANNED";
        this.steps = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
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

    public List<MigrationStepDto> getSteps() {
        return steps;
    }

    public void setSteps(List<MigrationStepDto> steps) {
        this.steps = steps;
    }

    public void addStep(MigrationStepDto step) {
        this.steps.add(step);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }
}
