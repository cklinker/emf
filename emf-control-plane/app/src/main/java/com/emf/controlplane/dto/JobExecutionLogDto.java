package com.emf.controlplane.dto;

import com.emf.controlplane.entity.JobExecutionLog;

import java.time.Instant;

public class JobExecutionLogDto {

    private String id;
    private String jobId;
    private String status;
    private int recordsProcessed;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Integer durationMs;

    public static JobExecutionLogDto fromEntity(JobExecutionLog entity) {
        JobExecutionLogDto dto = new JobExecutionLogDto();
        dto.setId(entity.getId());
        dto.setJobId(entity.getJob().getId());
        dto.setStatus(entity.getStatus());
        dto.setRecordsProcessed(entity.getRecordsProcessed());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setDurationMs(entity.getDurationMs());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
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
