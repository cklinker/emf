package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateBulkJobRequest;
import com.emf.controlplane.entity.BulkJob;
import com.emf.controlplane.entity.BulkJobResult;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.BulkJobRepository;
import com.emf.controlplane.repository.BulkJobResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BulkJobService {

    private static final Logger log = LoggerFactory.getLogger(BulkJobService.class);

    private final BulkJobRepository jobRepository;
    private final BulkJobResultRepository resultRepository;

    public BulkJobService(BulkJobRepository jobRepository,
                          BulkJobResultRepository resultRepository) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public List<BulkJob> listJobs(String tenantId) {
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public BulkJob getJob(String id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BulkJob", id));
    }

    @Transactional
    @SetupAudited(section = "BulkJobs", entityType = "BulkJob")
    public BulkJob createJob(String tenantId, String userId, CreateBulkJobRequest request) {
        log.info("Creating bulk job for collection '{}', operation '{}', tenant: {}",
                request.getCollectionId(), request.getOperation(), tenantId);

        BulkJob job = new BulkJob();
        job.setTenantId(tenantId);
        job.setCollectionId(request.getCollectionId());
        job.setOperation(request.getOperation());
        job.setCreatedBy(userId);
        job.setExternalIdField(request.getExternalIdField());
        job.setBatchSize(request.getBatchSize() != null ? request.getBatchSize() : 200);

        List<Map<String, Object>> records = request.getRecords();
        int totalRecords = records != null ? records.size() : 0;
        job.setTotalRecords(totalRecords);

        job = jobRepository.save(job);

        // Create placeholder results for each record
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                BulkJobResult result = new BulkJobResult();
                result.setBulkJob(job);
                result.setRecordIndex(i);
                result.setStatus("PENDING");
                job.getResults().add(result);
            }
            jobRepository.save(job);
        }

        return job;
    }

    @Transactional
    @SetupAudited(section = "BulkJobs", entityType = "BulkJob")
    public BulkJob abortJob(String id) {
        log.info("Aborting bulk job: {}", id);
        BulkJob job = getJob(id);
        job.setStatus("ABORTED");
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<BulkJobResult> getResults(String jobId) {
        // Verify job exists
        getJob(jobId);
        return resultRepository.findByBulkJobIdOrderByRecordIndexAsc(jobId);
    }

    @Transactional(readOnly = true)
    public List<BulkJobResult> getErrorResults(String jobId) {
        // Verify job exists
        getJob(jobId);
        return resultRepository.findByBulkJobIdAndStatus(jobId, "ERROR");
    }
}
