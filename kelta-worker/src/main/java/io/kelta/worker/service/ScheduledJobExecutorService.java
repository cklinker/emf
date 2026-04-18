package io.kelta.worker.service;

import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.worker.util.TenantContextUtils;
import io.kelta.runtime.flow.InitialStateBuilder;
import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.worker.repository.DataExportRepository;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes scheduled jobs that are due based on their cron expression.
 *
 * <p>Polled by {@link io.kelta.worker.config.SchedulerConfig} on a fixed interval.
 * Uses {@code SELECT FOR UPDATE SKIP LOCKED} via {@link ScheduledJobRepository}
 * for leader election — only one worker instance executes each job.
 *
 * <p>Supports job types:
 * <ul>
 *   <li><b>FLOW</b> — executes a flow definition via {@link FlowEngine}</li>
 *   <li><b>SCRIPT</b> — executes a JavaScript script via {@link ScriptExecutor}</li>
 *   <li><b>REPORT_EXPORT</b> — executes a report and exports as CSV via {@link ReportExecutionService}</li>
 *   <li><b>DATA_EXPORT</b> — triggers a tenant data export via {@link DataExportService}</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Service
public class ScheduledJobExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobExecutorService.class);

    private final ScheduledJobRepository repository;
    private final FlowEngine flowEngine;
    private final InitialStateBuilder initialStateBuilder;
    private final ObjectMapper objectMapper;
    private final ScriptExecutor scriptExecutor;
    private final ReportExecutionService reportExecutionService;
    private final DataExportService dataExportService;
    private final DataExportRepository dataExportRepository;

    public ScheduledJobExecutorService(ScheduledJobRepository repository,
                                        FlowEngine flowEngine,
                                        InitialStateBuilder initialStateBuilder,
                                        ObjectMapper objectMapper,
                                        ScriptExecutor scriptExecutor,
                                        ReportExecutionService reportExecutionService,
                                        DataExportService dataExportService,
                                        DataExportRepository dataExportRepository) {
        this.repository = repository;
        this.flowEngine = flowEngine;
        this.initialStateBuilder = initialStateBuilder;
        this.objectMapper = objectMapper;
        this.scriptExecutor = scriptExecutor;
        this.reportExecutionService = reportExecutionService;
        this.dataExportService = dataExportService;
        this.dataExportRepository = dataExportRepository;
    }

    /**
     * Finds and executes all due scheduled jobs.
     * Called on each poll cycle by SchedulerConfig.
     */
    @SuppressWarnings("unchecked")
    public void executeAll() {
        // Polling `scheduled_job` spans every tenant (leader election via
        // SELECT … FOR UPDATE SKIP LOCKED). Bind the platform sentinel so the
        // cross-tenant read bypasses RLS explicitly — per-job execution below
        // rebinds the job's real tenantId before any tenant-scoped work.
        List<Map<String, Object>> dueJobs = TenantContext.callAsPlatform(repository::findDueJobs);
        if (dueJobs.isEmpty()) {
            return;
        }

        log.debug("Found {} due scheduled jobs", dueJobs.size());

        for (Map<String, Object> job : dueJobs) {
            String jobId = (String) job.get("id");
            String tenantId = (String) job.get("tenant_id");
            String jobType = (String) job.get("job_type");
            String jobReferenceId = (String) job.get("job_reference_id");
            String cronExpression = (String) job.get("cron_expression");
            String timezone = (String) job.get("timezone");

            Instant startedAt = Instant.now();

            try {
                TenantContextUtils.withTenant(tenantId, () -> {
                    switch (jobType) {
                        case "FLOW" -> executeFlowJob(jobId, tenantId, jobReferenceId, cronExpression, timezone, startedAt);
                        case "SCRIPT" -> executeScriptJob(jobId, tenantId, jobReferenceId, cronExpression, timezone, startedAt);
                        case "REPORT_EXPORT" -> executeReportExportJob(jobId, tenantId, jobReferenceId, cronExpression, timezone, startedAt);
                        case "DATA_EXPORT" -> executeDataExportJob(jobId, tenantId, jobReferenceId, cronExpression, timezone, startedAt);
                        default -> {
                            log.warn("Unsupported job type '{}' for job {}, skipping", jobType, jobId);
                            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
                            repository.updateAfterExecution(jobId, "SKIPPED", startedAt, nextRun);
                        }
                    }
                });
            } catch (Exception e) {
                Instant completedAt = Instant.now();
                long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
                Instant nextRun = safeCalculateNextRun(cronExpression, timezone);

                repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
                repository.insertExecutionLog(jobId, "FAILED", e.getMessage(), startedAt, completedAt, durationMs);

                log.error("Scheduled job failed: jobId={}, type={}, refId={}, tenantId={}, error={}, duration={}ms",
                        jobId, jobType, jobReferenceId, tenantId, e.getMessage(), durationMs);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // FLOW execution
    // ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void executeFlowJob(String jobId, String tenantId, String flowReferenceId,
                                 String cronExpression, String timezone, Instant startedAt) throws Exception {
        var flowOpt = repository.findFlowById(flowReferenceId);
        if (flowOpt.isEmpty()) {
            log.error("Flow {} not found for scheduled job {}", flowReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Flow not found: " + flowReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        Map<String, Object> flow = flowOpt.get();
        boolean flowActive = (Boolean) flow.get("active");
        if (!flowActive) {
            log.warn("Flow {} is inactive for scheduled job {}", flowReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Flow inactive: " + flowReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        // Build initial state
        String executionId = UUID.randomUUID().toString();
        Object triggerConfigRaw = flow.get("trigger_config");
        Map<String, Object> triggerConfig = null;
        if (triggerConfigRaw instanceof String s) {
            triggerConfig = objectMapper.readValue(s, Map.class);
        } else if (triggerConfigRaw instanceof Map) {
            triggerConfig = (Map<String, Object>) triggerConfigRaw;
        }

        Map<String, Object> initialState = initialStateBuilder.buildFromSchedule(
                triggerConfig, tenantId, flowReferenceId, executionId);

        String definitionJson = flow.get("definition") instanceof String s
                ? s : objectMapper.writeValueAsString(flow.get("definition"));

        // Execute flow (async — returns immediately)
        String flowExecutionId = flowEngine.startExecution(
                tenantId, flowReferenceId, definitionJson, initialState, null, false);

        Instant completedAt = Instant.now();
        long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

        Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
        repository.updateAfterExecution(jobId, "SUCCESS", startedAt, nextRun);
        repository.insertExecutionLog(jobId, "SUCCESS", null, startedAt, completedAt, durationMs);

        log.info("Scheduled job executed: jobId={}, type=FLOW, flowId={}, tenantId={}, executionId={}, duration={}ms",
                jobId, flowReferenceId, tenantId, flowExecutionId, durationMs);
    }

    // ---------------------------------------------------------------------------
    // SCRIPT execution
    // ---------------------------------------------------------------------------

    private void executeScriptJob(String jobId, String tenantId, String scriptReferenceId,
                                   String cronExpression, String timezone, Instant startedAt) {
        var scriptOpt = repository.findScriptById(scriptReferenceId);
        if (scriptOpt.isEmpty()) {
            log.error("Script {} not found for scheduled job {}", scriptReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Script not found: " + scriptReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        Map<String, Object> script = scriptOpt.get();
        boolean active = Boolean.TRUE.equals(script.get("active"));
        if (!active) {
            log.warn("Script {} is inactive for scheduled job {}", scriptReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Script inactive: " + scriptReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        String sourceCode = (String) script.get("source_code");
        if (sourceCode == null || sourceCode.isBlank()) {
            log.error("Script {} has no source code for scheduled job {}", scriptReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Script has no source code: " + scriptReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        // Build bindings for the script context
        Map<String, Object> bindings = Map.of(
                "context", Map.of(
                        "tenantId", tenantId,
                        "jobId", jobId,
                        "scriptId", scriptReferenceId,
                        "triggerType", "SCHEDULED"
                )
        );

        ScriptExecutionResult result = scriptExecutor.execute(
                new ScriptExecutionRequest(sourceCode, bindings));

        Instant completedAt = Instant.now();
        long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);

        if (result.success()) {
            repository.updateAfterExecution(jobId, "SUCCESS", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "SUCCESS", null, startedAt, completedAt, durationMs);
            log.info("Scheduled job executed: jobId={}, type=SCRIPT, scriptId={}, tenantId={}, duration={}ms",
                    jobId, scriptReferenceId, tenantId, durationMs);
        } else {
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", result.errorMessage(), startedAt, completedAt, durationMs);
            log.error("Scheduled job failed: jobId={}, type=SCRIPT, scriptId={}, tenantId={}, error={}, duration={}ms",
                    jobId, scriptReferenceId, tenantId, result.errorMessage(), durationMs);
        }
    }

    // ---------------------------------------------------------------------------
    // REPORT_EXPORT execution
    // ---------------------------------------------------------------------------

    private void executeReportExportJob(String jobId, String tenantId, String reportReferenceId,
                                         String cronExpression, String timezone, Instant startedAt) {
        var reportOpt = repository.findReportById(reportReferenceId);
        if (reportOpt.isEmpty()) {
            log.error("Report {} not found for scheduled job {}", reportReferenceId, jobId);
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Report not found: " + reportReferenceId,
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        Map<String, Object> reportConfig = reportOpt.get();

        // Execute report export as CSV into a StringWriter
        StringWriter csvWriter = new StringWriter();
        try {
            reportExecutionService.exportCsv(reportConfig, csvWriter);
        } catch (Exception e) {
            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Report export failed: " + e.getMessage(),
                    startedAt, completedAt, durationMs);
            log.error("Scheduled job failed: jobId={}, type=REPORT_EXPORT, reportId={}, tenantId={}, error={}, duration={}ms",
                    jobId, reportReferenceId, tenantId, e.getMessage(), durationMs);
            return;
        }

        Instant completedAt = Instant.now();
        long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);

        // Count CSV rows (subtract 1 for header line)
        String csvOutput = csvWriter.toString();
        long lineCount = csvOutput.lines().count();
        int recordsProcessed = (int) Math.max(0, lineCount - 1);

        repository.updateAfterExecution(jobId, "SUCCESS", startedAt, nextRun);
        repository.insertExecutionLog(jobId, "SUCCESS", null, startedAt, completedAt, durationMs);

        log.info("Scheduled job executed: jobId={}, type=REPORT_EXPORT, reportId={}, tenantId={}, records={}, duration={}ms",
                jobId, reportReferenceId, tenantId, recordsProcessed, durationMs);
    }

    // ---------------------------------------------------------------------------
    // DATA_EXPORT execution
    // ---------------------------------------------------------------------------

    private void executeDataExportJob(String jobId, String tenantId, String exportConfigJson,
                                       String cronExpression, String timezone, Instant startedAt) {
        // The job_reference_id stores an export configuration as JSON with name, scope, collectionIds, format
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(exportConfigJson, Map.class);
        } catch (Exception e) {
            Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
            repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
            repository.insertExecutionLog(jobId, "FAILED", "Invalid export config: " + e.getMessage(),
                    startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            return;
        }

        String name = (String) config.getOrDefault("name", "Scheduled Export");
        String exportScope = (String) config.getOrDefault("exportScope", "FULL");
        String format = (String) config.getOrDefault("format", "CSV");
        @SuppressWarnings("unchecked")
        List<String> collectionIds = (List<String>) config.get("collectionIds");

        // Create and execute the export
        String exportId = dataExportService.createExport(
                tenantId, name, null, exportScope, collectionIds, format, "system:scheduled-job:" + jobId);
        dataExportService.executeExport(exportId, tenantId);

        Instant completedAt = Instant.now();
        long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);

        repository.updateAfterExecution(jobId, "SUCCESS", startedAt, nextRun);
        repository.insertExecutionLog(jobId, "SUCCESS", null, startedAt, completedAt, durationMs);

        log.info("Scheduled job executed: jobId={}, type=DATA_EXPORT, exportId={}, tenantId={}, duration={}ms",
                jobId, exportId, tenantId, durationMs);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Instant safeCalculateNextRun(String cronExpression, String timezone) {
        try {
            return ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
        } catch (Exception e) {
            log.warn("Failed to calculate next run time: {}", e.getMessage());
            return null;
        }
    }
}
