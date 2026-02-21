package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scheduled_job")
public class ScheduledJob extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "job_type", nullable = false, length = 20)
    private String jobType;

    @Column(name = "job_reference_id", length = 36)
    private String jobReferenceId;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    public ScheduledJob() { super(); }

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
}
