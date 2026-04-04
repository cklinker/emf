package io.kelta.worker.controller;

import tools.jackson.databind.ObjectMapper;
import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.repository.BulkJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for managing bulk data operations.
 *
 * <p>Provides endpoints to create bulk jobs (INSERT, UPDATE, UPSERT, DELETE),
 * check job status, list jobs, retrieve per-record results, and abort jobs.
 *
 * <p>Bulk jobs are queued and processed asynchronously by
 * {@link io.kelta.worker.service.BulkJobProcessorService}.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/bulk-jobs")
public class BulkOperationsController {

    private static final Logger log = LoggerFactory.getLogger(BulkOperationsController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final int MAX_INLINE_RECORDS = 10_000;
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final Set<String> VALID_OPERATIONS = Set.of("INSERT", "UPDATE", "UPSERT", "DELETE");

    private final BulkJobRepository bulkJobRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public BulkOperationsController(BulkJobRepository bulkJobRepository,
                                     ObjectMapper objectMapper,
                                     JdbcTemplate jdbcTemplate) {
        this.bulkJobRepository = bulkJobRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new bulk job with inline data payload.
     *
     * <p>The job is queued for asynchronous processing. Poll the job status
     * endpoint to track progress.
     */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> createJob(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody Map<String, Object> body) {

        String collectionId = (String) body.get("collectionId");
        String operation = (String) body.get("operation");
        Object recordsObj = body.get("records");
        String externalIdField = (String) body.get("externalIdField");
        Integer batchSize = body.get("batchSize") instanceof Number n ? n.intValue() : null;

        // Validate required fields
        if (collectionId == null || collectionId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Missing field", "collectionId is required"));
        }
        if (operation == null || !VALID_OPERATIONS.contains(operation.toUpperCase())) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Invalid operation",
                            "operation must be one of: INSERT, UPDATE, UPSERT, DELETE"));
        }
        if (recordsObj == null || !(recordsObj instanceof List<?> recordsList) || recordsList.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Missing records",
                            "records must be a non-empty array"));
        }

        operation = operation.toUpperCase();
        List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;

        if (records.size() > MAX_INLINE_RECORDS) {
            return ResponseEntity.unprocessableEntity().body(
                    JsonApiResponseBuilder.error("422", "Too many records",
                            "Maximum " + MAX_INLINE_RECORDS + " records per request"));
        }

        // Validate UPSERT external ID field
        if ("UPSERT".equals(operation) && (externalIdField == null || externalIdField.isBlank())) {
            // Allow upsert by 'id' when no external ID field is provided
            externalIdField = null;
        }

        // Validate collection exists
        if (!collectionExists(collectionId, tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    JsonApiResponseBuilder.error("404", "Collection not found",
                            "Collection " + collectionId + " not found or inactive"));
        }

        // Validate batch size
        int effectiveBatchSize = batchSize != null
                ? Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE)
                : DEFAULT_BATCH_SIZE;

        // Serialize records to JSON
        String dataPayload;
        try {
            dataPayload = objectMapper.writeValueAsString(records);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Serialization error",
                            "Failed to serialize records: " + e.getMessage()));
        }

        // Create the job
        String jobId = bulkJobRepository.create(
                tenantId, collectionId, operation,
                "application/json", effectiveBatchSize, externalIdField,
                userEmail, dataPayload, null, records.size());

        securityLog.info("security_event=BULK_JOB_CREATED user={} tenant={} job={} operation={} records={}",
                userEmail, tenantId, jobId, operation, records.size());

        log.info("Bulk job created: jobId={}, operation={}, collection={}, records={}, batchSize={}",
                jobId, operation, collectionId, records.size(), effectiveBatchSize);

        // Return the created job
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", "QUEUED");
        attributes.put("operation", operation);
        attributes.put("collectionId", collectionId);
        attributes.put("totalRecords", records.size());
        attributes.put("processedRecords", 0);
        attributes.put("successRecords", 0);
        attributes.put("errorRecords", 0);
        attributes.put("batchSize", effectiveBatchSize);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JsonApiResponseBuilder.single("bulk-jobs", jobId, attributes));
    }

    /**
     * Gets the status and progress of a bulk job.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getJob(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        Optional<Map<String, Object>> jobOpt = bulkJobRepository.findByIdAndTenant(id, tenantId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> job = jobOpt.get();
        return ResponseEntity.ok(JsonApiResponseBuilder.single("bulk-jobs", id, toJobAttributes(job)));
    }

    /**
     * Lists bulk jobs for the current tenant.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        int effectiveOffset = Math.max(offset, 0);

        List<Map<String, Object>> jobs = bulkJobRepository.findByTenant(tenantId, effectiveLimit, effectiveOffset);
        List<Map<String, Object>> records = jobs.stream()
                .map(job -> {
                    Map<String, Object> record = toJobAttributes(job);
                    record.put("id", job.get("id"));
                    return record;
                })
                .toList();

        return ResponseEntity.ok(JsonApiResponseBuilder.collection("bulk-jobs", records));
    }

    /**
     * Gets per-record results for a bulk job.
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<Map<String, Object>> getResults(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String status) {

        // Verify job belongs to tenant
        Optional<Map<String, Object>> jobOpt = bulkJobRepository.findByIdAndTenant(id, tenantId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int effectiveLimit = Math.min(Math.max(limit, 1), 200);
        int effectiveOffset = Math.max(offset, 0);
        String statusFilter = status != null && Set.of("SUCCESS", "FAILURE").contains(status.toUpperCase())
                ? status.toUpperCase() : null;

        List<Map<String, Object>> results = bulkJobRepository.findResults(
                id, tenantId, effectiveLimit, effectiveOffset, statusFilter);
        int totalCount = bulkJobRepository.countResults(id, tenantId, statusFilter);

        List<Map<String, Object>> records = results.stream()
                .map(r -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("id", r.get("id"));
                    record.put("recordIndex", r.get("record_index"));
                    record.put("recordId", r.get("record_id"));
                    record.put("status", r.get("status"));
                    record.put("errorMessage", r.get("error_message"));
                    return record;
                })
                .toList();

        Map<String, Object> meta = Map.of(
                "totalCount", totalCount,
                "limit", effectiveLimit,
                "offset", effectiveOffset);

        return ResponseEntity.ok(JsonApiResponseBuilder.collection("bulk-job-results", records, meta));
    }

    /**
     * Aborts a queued or in-progress bulk job.
     */
    @PostMapping("/{id}/abort")
    public ResponseEntity<Map<String, Object>> abortJob(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-User-Email") String userEmail) {

        int updated = bulkJobRepository.abort(id, tenantId);
        if (updated == 0) {
            Optional<Map<String, Object>> jobOpt = bulkJobRepository.findByIdAndTenant(id, tenantId);
            if (jobOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.unprocessableEntity().body(
                    JsonApiResponseBuilder.error("422", "Cannot abort",
                            "Job is not in QUEUED or PROCESSING status"));
        }

        securityLog.info("security_event=BULK_JOB_ABORTED user={} tenant={} job={}",
                userEmail, tenantId, id);

        Optional<Map<String, Object>> jobOpt = bulkJobRepository.findByIdAndTenant(id, tenantId);
        Map<String, Object> job = jobOpt.orElseGet(Map::of);
        return ResponseEntity.ok(JsonApiResponseBuilder.single("bulk-jobs", id, toJobAttributes(job)));
    }

    private Map<String, Object> toJobAttributes(Map<String, Object> job) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", job.get("status"));
        attributes.put("operation", job.get("operation"));
        attributes.put("collectionId", job.get("collection_id"));
        attributes.put("totalRecords", job.get("total_records"));
        attributes.put("processedRecords", job.get("processed_records"));
        attributes.put("successRecords", job.get("success_records"));
        attributes.put("errorRecords", job.get("error_records"));
        attributes.put("batchSize", job.get("batch_size"));
        attributes.put("externalIdField", job.get("external_id_field"));
        attributes.put("contentType", job.get("content_type"));
        attributes.put("errorMessage", job.get("error_message"));
        attributes.put("createdBy", job.get("created_by"));
        attributes.put("startedAt", job.get("started_at"));
        attributes.put("completedAt", job.get("completed_at"));
        attributes.put("createdAt", job.get("created_at"));
        attributes.put("updatedAt", job.get("updated_at"));
        return attributes;
    }

    private boolean collectionExists(String collectionId, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM collection WHERE id = ? AND tenant_id = ? AND active = true LIMIT 1",
                collectionId, tenantId);
        return !rows.isEmpty();
    }
}
