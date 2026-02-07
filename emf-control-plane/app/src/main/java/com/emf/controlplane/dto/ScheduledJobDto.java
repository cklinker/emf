package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ScheduledJob;

import java.time.Instant;

public class ScheduledJobDto {

    private String id;
    private String name;
    private String description;
    private String jobType;
    private String jobReferenceId;
    private String cronExpression;
    private String timezone;
    private boolean active;
    private Instant lastRunAt;
    private String lastStatus;
    private Instant nextRunAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScheduledJobDto fromEntity(ScheduledJob entity) {
        ScheduledJobDto dto = new ScheduledJobDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setJobType(entity.getJobType());
        dto.setJobReferenceId(entity.getJobReferenceId());
        dto.setCronExpression(entity.getCronExpression());
        dto.setTimezone(entity.getTimezone());
        dto.setActive(entity.isActive());
        dto.setLastRunAt(entity.getLastRunAt());
        dto.setLastStatus(entity.getLastStatus());
        dto.setNextRunAt(entity.getNextRunAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getJobReferenceId() { return jobReferenceId; }
    public void setJobReferenceId(String jobReferenceId) { this.jobReferenceId = jobReferenceId; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
