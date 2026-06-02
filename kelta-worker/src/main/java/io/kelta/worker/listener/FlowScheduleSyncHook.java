package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the {@code scheduled_job} table in sync with SCHEDULED flow configurations.
 *
 * <p>When a flow with {@code flowType = SCHEDULED} is created or updated, this hook
 * upserts the corresponding {@code scheduled_job} row using the cron expression from
 * {@code triggerConfig}. On flow deletion (or type change away from SCHEDULED), the job
 * is removed.
 *
 * <p>This bridges the gap between saving a flow's trigger configuration in the UI and
 * the scheduler executor picking it up — the executor reads from {@code scheduled_job},
 * so the row must exist and stay current.
 *
 * @since 1.0.0
 */
public class FlowScheduleSyncHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FlowScheduleSyncHook.class);

    private final ScheduledJobRepository scheduledJobRepository;
    private final ObjectMapper objectMapper;

    public FlowScheduleSyncHook(ScheduledJobRepository scheduledJobRepository, ObjectMapper objectMapper) {
        this.scheduledJobRepository = scheduledJobRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getCollectionName() {
        return "flows";
    }

    @Override
    public int getOrder() {
        // Run after FlowConfigEventPublisher (100) — job sync is a side effect, not a guard
        return 150;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        if (!"SCHEDULED".equals(record.get("flowType"))) return;
        syncScheduledJob(record, tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        String newType = asString(record.get("flowType"));
        String prevType = previous != null ? asString(previous.get("flowType")) : null;

        if ("SCHEDULED".equals(newType)) {
            syncScheduledJob(record, tenantId);
        } else if ("SCHEDULED".equals(prevType)) {
            scheduledJobRepository.deleteForFlow(id, tenantId);
        }
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        scheduledJobRepository.deleteForFlow(id, tenantId);
    }

    private void syncScheduledJob(Map<String, Object> record, String tenantId) {
        String flowId = asString(record.get("id"));
        String flowName = asString(record.get("name"));
        Object activeObj = record.get("active");
        boolean active = activeObj instanceof Boolean b ? b : true;

        Map<String, Object> triggerConfig = extractTriggerConfig(record);
        if (triggerConfig == null || triggerConfig.isEmpty()) {
            log.debug("SCHEDULED flow {} has no triggerConfig — skipping job sync", flowId);
            return;
        }

        String cron = asString(triggerConfig.get("cron"));
        String timezone = asString(triggerConfig.get("timezone"));

        if (cron == null || cron.isBlank()) {
            log.debug("SCHEDULED flow {} triggerConfig has no cron — skipping job sync", flowId);
            return;
        }

        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            log.warn("SCHEDULED flow {} has invalid cron '{}' — skipping job sync: {}", flowId, cron, e.getMessage());
            return;
        }

        Instant nextRunAt = active ? ScheduledJobRepository.calculateNextRunAt(cron, timezone) : null;

        Optional<Map<String, Object>> existing = scheduledJobRepository.findByFlowId(flowId, tenantId);
        if (existing.isEmpty()) {
            String createdBy = resolveCreatedBy(record, flowId);
            if (createdBy == null) {
                log.warn("Cannot create scheduled_job for flow {} — could not resolve createdBy", flowId);
                return;
            }
            scheduledJobRepository.insertForFlow(flowId, tenantId, flowName, cron, timezone, active, nextRunAt, createdBy);
        } else {
            String jobId = asString(existing.get().get("id"));
            scheduledJobRepository.updateForFlow(jobId, flowName, cron, timezone, active, nextRunAt);
        }
    }

    private String resolveCreatedBy(Map<String, Object> record, String flowId) {
        String fromRecord = asString(record.get("createdBy"));
        if (fromRecord == null) fromRecord = asString(record.get("created_by"));
        if (fromRecord != null) return fromRecord;
        return scheduledJobRepository.findFlowCreatedBy(flowId).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTriggerConfig(Map<String, Object> record) {
        Object raw = record.get("triggerConfig");
        if (raw == null) raw = record.get("trigger_config");
        if (raw == null) return null;
        if (raw instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (raw instanceof String s && !s.isBlank()) {
            try {
                Object parsed = objectMapper.readValue(s, Object.class);
                if (parsed instanceof Map<?, ?> m) return (Map<String, Object>) m;
            } catch (Exception e) {
                log.warn("Failed to parse triggerConfig JSON for flow: {}", e.getMessage());
            }
        }
        return null;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
