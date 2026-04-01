package io.kelta.worker.service;

import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.InitialStateBuilder;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 * <p>Currently supports job_type=FLOW only. Jobs are executed via
 * {@link FlowEngine#startExecution} which is async.
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

    public ScheduledJobExecutorService(ScheduledJobRepository repository,
                                        FlowEngine flowEngine,
                                        InitialStateBuilder initialStateBuilder,
                                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.flowEngine = flowEngine;
        this.initialStateBuilder = initialStateBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds and executes all due scheduled jobs.
     * Called on each poll cycle by SchedulerConfig.
     */
    @SuppressWarnings("unchecked")
    public void executeAll() {
        List<Map<String, Object>> dueJobs = repository.findDueJobs();
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
                if (!"FLOW".equals(jobType)) {
                    log.warn("Unsupported job type '{}' for job {}, skipping", jobType, jobId);
                    Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
                    repository.updateAfterExecution(jobId, "SKIPPED", startedAt, nextRun);
                    continue;
                }

                // Load flow definition
                var flowOpt = repository.findFlowById(jobReferenceId);
                if (flowOpt.isEmpty()) {
                    log.error("Flow {} not found for scheduled job {}", jobReferenceId, jobId);
                    Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
                    repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
                    repository.insertExecutionLog(jobId, "FAILED", "Flow not found: " + jobReferenceId,
                            startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
                    continue;
                }

                Map<String, Object> flow = flowOpt.get();
                boolean flowActive = (Boolean) flow.get("active");
                if (!flowActive) {
                    log.warn("Flow {} is inactive for scheduled job {}", jobReferenceId, jobId);
                    Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
                    repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
                    repository.insertExecutionLog(jobId, "FAILED", "Flow inactive: " + jobReferenceId,
                            startedAt, Instant.now(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
                    continue;
                }

                // Set tenant context for flow execution
                TenantContext.set(tenantId);

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
                        triggerConfig, tenantId, jobReferenceId, executionId);

                String definitionJson = flow.get("definition") instanceof String s
                        ? s : objectMapper.writeValueAsString(flow.get("definition"));

                // Execute flow (async — returns immediately)
                String flowExecutionId = flowEngine.startExecution(
                        tenantId, jobReferenceId, definitionJson, initialState, null, false);

                Instant completedAt = Instant.now();
                long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

                // Update job and log success — always calculate next from NOW
                Instant nextRun = ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
                repository.updateAfterExecution(jobId, "SUCCESS", startedAt, nextRun);
                repository.insertExecutionLog(jobId, "SUCCESS", null, startedAt, completedAt, durationMs);

                log.info("Scheduled job executed: jobId={}, flowId={}, tenantId={}, executionId={}, status=SUCCESS, duration={}ms",
                        jobId, jobReferenceId, tenantId, flowExecutionId, durationMs);

            } catch (Exception e) {
                Instant completedAt = Instant.now();
                long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
                Instant nextRun = safeCalculateNextRun(cronExpression, timezone);

                repository.updateAfterExecution(jobId, "FAILED", startedAt, nextRun);
                repository.insertExecutionLog(jobId, "FAILED", e.getMessage(), startedAt, completedAt, durationMs);

                log.error("Scheduled job failed: jobId={}, flowId={}, tenantId={}, error={}, duration={}ms",
                        jobId, jobReferenceId, tenantId, e.getMessage(), durationMs);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private Instant safeCalculateNextRun(String cronExpression, String timezone) {
        try {
            return ScheduledJobRepository.calculateNextRunAt(cronExpression, timezone);
        } catch (Exception e) {
            log.warn("Failed to calculate next run time: {}", e.getMessage());
            return null;
        }
    }
}
