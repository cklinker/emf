package com.emf.controlplane.dto;

import com.emf.controlplane.entity.MigrationRun;
import com.emf.controlplane.entity.MigrationStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO representing a migration run.
 * Contains the migration execution details including all steps and their status.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>7.2: Return the history of executed migrations</li>
 *   <li>7.3: Return migration run details including all steps and their status</li>
 *   <li>7.4: Track each migration step with success/failure status</li>
 * </ul>
 */
public class MigrationRunDto {

    private String id;
    private String collectionId;
    private Integer fromVersion;
    private Integer toVersion;
    private String status;
    private String errorMessage;
    private List<MigrationStepDto> steps;
    private Instant createdAt;
    private Instant updatedAt;

    public MigrationRunDto() {
        this.steps = new ArrayList<>();
    }

    public MigrationRunDto(String id, String collectionId, Integer fromVersion, 
                          Integer toVersion, String status) {
        this.id = id;
        this.collectionId = collectionId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.status = status;
        this.steps = new ArrayList<>();
    }

    /**
     * Creates a DTO from a MigrationRun entity.
     *
     * @param entity The MigrationRun entity
     * @return The corresponding DTO
     */
    public static MigrationRunDto fromEntity(MigrationRun entity) {
        MigrationRunDto dto = new MigrationRunDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollectionId());
        dto.setFromVersion(entity.getFromVersion());
        dto.setToVersion(entity.getToVersion());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getSteps() != null) {
            List<MigrationStepDto> stepDtos = entity.getSteps().stream()
                    .map(MigrationRunDto::stepToDto)
                    .collect(Collectors.toList());
            dto.setSteps(stepDtos);
        }

        return dto;
    }

    /**
     * Converts a MigrationStep entity to a DTO.
     *
     * @param step The MigrationStep entity
     * @return The corresponding DTO
     */
    private static MigrationStepDto stepToDto(MigrationStep step) {
        MigrationStepDto dto = new MigrationStepDto();
        dto.setId(step.getId());
        dto.setStepNumber(step.getStepNumber());
        dto.setOperation(step.getOperation());
        dto.setStatus(step.getStatus());
        dto.setDetails(step.getDetails());
        dto.setErrorMessage(step.getErrorMessage());
        dto.setCreatedAt(step.getCreatedAt());
        dto.setUpdatedAt(step.getUpdatedAt());
        return dto;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }

    public int getCompletedStepCount() {
        if (steps == null) return 0;
        return (int) steps.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()) || "SUCCESS".equals(s.getStatus()))
                .count();
    }

    public int getFailedStepCount() {
        if (steps == null) return 0;
        return (int) steps.stream()
                .filter(s -> "FAILED".equals(s.getStatus()))
                .count();
    }

    @Override
    public String toString() {
        return "MigrationRunDto{" +
                "id='" + id + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", fromVersion=" + fromVersion +
                ", toVersion=" + toVersion +
                ", status='" + status + '\'' +
                '}';
    }
}
