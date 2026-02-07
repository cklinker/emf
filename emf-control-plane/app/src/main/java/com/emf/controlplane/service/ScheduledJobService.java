package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateScheduledJobRequest;
import com.emf.controlplane.entity.JobExecutionLog;
import com.emf.controlplane.entity.ScheduledJob;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.JobExecutionLogRepository;
import com.emf.controlplane.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScheduledJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobService.class);

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionLogRepository logRepository;

    public ScheduledJobService(ScheduledJobRepository jobRepository,
                               JobExecutionLogRepository logRepository) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduledJob> listJobs(String tenantId) {
        return jobRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public ScheduledJob getJob(String id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduledJob", id));
    }

    @Transactional
    @SetupAudited(section = "ScheduledJobs", entityType = "ScheduledJob")
    public ScheduledJob createJob(String tenantId, String userId, CreateScheduledJobRequest request) {
        log.info("Creating scheduled job '{}' for tenant: {}", request.getName(), tenantId);

        ScheduledJob job = new ScheduledJob();
        job.setTenantId(tenantId);
        job.setName(request.getName());
        job.setDescription(request.getDescription());
        job.setJobType(request.getJobType());
        job.setJobReferenceId(request.getJobReferenceId());
        job.setCronExpression(request.getCronExpression());
        job.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        job.setActive(request.getActive() != null ? request.getActive() : true);
        job.setCreatedBy(userId);

        return jobRepository.save(job);
    }

    @Transactional
    @SetupAudited(section = "ScheduledJobs", entityType = "ScheduledJob")
    public ScheduledJob updateJob(String id, CreateScheduledJobRequest request) {
        log.info("Updating scheduled job: {}", id);
        ScheduledJob job = getJob(id);

        if (request.getName() != null) job.setName(request.getName());
        if (request.getDescription() != null) job.setDescription(request.getDescription());
        if (request.getJobType() != null) job.setJobType(request.getJobType());
        if (request.getJobReferenceId() != null) job.setJobReferenceId(request.getJobReferenceId());
        if (request.getCronExpression() != null) job.setCronExpression(request.getCronExpression());
        if (request.getTimezone() != null) job.setTimezone(request.getTimezone());
        if (request.getActive() != null) job.setActive(request.getActive());

        return jobRepository.save(job);
    }

    @Transactional
    @SetupAudited(section = "ScheduledJobs", entityType = "ScheduledJob")
    public void deleteJob(String id) {
        log.info("Deleting scheduled job: {}", id);
        ScheduledJob job = getJob(id);
        jobRepository.delete(job);
    }

    // --- Execution Logs ---

    @Transactional(readOnly = true)
    public List<JobExecutionLog> listExecutionLogs(String jobId) {
        return logRepository.findByJobIdOrderByStartedAtDesc(jobId);
    }
}
