package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_execution_log")
public class JobExecutionLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ScheduledJob job;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "records_processed")
    private int recordsProcessed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    public JobExecutionLog() { super(); }

    public ScheduledJob getJob() { return job; }
    public void setJob(ScheduledJob job) { this.job = job; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(int recordsProcessed) { this.recordsProcessed = recordsProcessed; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
}
