package io.kelta.worker.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.kelta.worker.repository.BulkJobRepository;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Polls for queued bulk jobs and processes them.
 *
 * <p>Polled by {@link io.kelta.worker.config.BulkJobProcessorConfig} on a fixed interval.
 * Uses {@code SELECT FOR UPDATE SKIP LOCKED} via {@link BulkJobRepository}
 * for leader election — only one worker instance processes each job.
 *
 * @since 1.0.0
 */
@Service
public class BulkJobProcessorService {

    private static final Logger log = LoggerFactory.getLogger(BulkJobProcessorService.class);

    private final BulkJobRepository bulkJobRepository;
    private final BulkOperationService bulkOperationService;
    private final ObjectMapper objectMapper;

    public BulkJobProcessorService(BulkJobRepository bulkJobRepository,
                                    BulkOperationService bulkOperationService,
                                    ObjectMapper objectMapper) {
        this.bulkJobRepository = bulkJobRepository;
        this.bulkOperationService = bulkOperationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds and executes all queued bulk jobs.
     * Called on each poll cycle by BulkJobProcessorConfig.
     */
    @SuppressWarnings("unchecked")
    public void processQueuedJobs() {
        List<Map<String, Object>> queuedJobs = bulkJobRepository.findQueuedJobs(10);
        if (queuedJobs.isEmpty()) {
            return;
        }

        log.debug("Found {} queued bulk jobs", queuedJobs.size());

        for (Map<String, Object> job : queuedJobs) {
            String jobId = (String) job.get("id");
            String tenantId = (String) job.get("tenant_id");
            String collectionId = (String) job.get("collection_id");
            String operation = (String) job.get("operation");
            int batchSize = ((Number) job.get("batch_size")).intValue();
            String externalIdField = (String) job.get("external_id_field");

            try {
                TenantContextUtils.withTenant(tenantId, () -> {
                    List<Map<String, Object>> records = parseDataPayload(job);
                    if (records == null || records.isEmpty()) {
                        bulkJobRepository.markFailed(jobId, "No records found in data payload");
                        return;
                    }
                    bulkOperationService.processJob(jobId, collectionId, operation,
                            records, batchSize, externalIdField);
                });
            } catch (Exception e) {
                log.error("Bulk job {} failed: {}", jobId, e.getMessage(), e);
                bulkJobRepository.markFailed(jobId, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDataPayload(Map<String, Object> job) throws Exception {
        Object dataPayload = job.get("data_payload");
        if (dataPayload == null) {
            return null;
        }

        if (dataPayload instanceof String jsonStr) {
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
        } else if (dataPayload instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        } else {
            // PostgreSQL JSONB may come as a PGobject — convert via string
            String json = dataPayload.toString();
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        }
    }
}
