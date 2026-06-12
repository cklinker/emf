package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.FlowRepository;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read endpoints exposing scheduled-job state per flow, plus a derived
 * {@code scheduleStatus} health flag that surfaces SCHEDULED flows whose
 * {@code scheduled_job} row is missing or stalled — the case where a flow
 * looks "scheduled" in the UI but is silently never run by the executor.
 *
 * <p>Tenant-scoped via {@link TenantContext}.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/flows")
public class FlowScheduleController {

    private static final Logger log = LoggerFactory.getLogger(FlowScheduleController.class);

    static final String SCHEDULED = "SCHEDULED";
    static final String STATUS_SYNCED = "SYNCED";
    static final String STATUS_UNSYNCED = "UNSYNCED";
    static final String STATUS_NOT_SCHEDULED = "NOT_SCHEDULED";

    private final FlowRepository flowRepository;
    private final ScheduledJobRepository scheduledJobRepository;

    public FlowScheduleController(FlowRepository flowRepository,
                                   ScheduledJobRepository scheduledJobRepository) {
        this.flowRepository = flowRepository;
        this.scheduledJobRepository = scheduledJobRepository;
    }

    @GetMapping("/{flowId}/schedule")
    public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String flowId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", "No tenant context"));
        }

        Optional<Map<String, Object>> flowOpt = flowRepository.findFlowTypeForTenant(flowId, tenantId);
        if (flowOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String flowType = (String) flowOpt.get().get("flow_type");
        Optional<Map<String, Object>> jobOpt = scheduledJobRepository.findScheduleForFlow(flowId, tenantId);

        Map<String, Object> attrs = new LinkedHashMap<>();

        if (jobOpt.isPresent()) {
            Map<String, Object> job = jobOpt.get();
            attrs.put("cron", job.get("cron_expression"));
            attrs.put("timezone", job.get("timezone"));
            attrs.put("active", job.get("active"));
            attrs.put("lastRunAt", instantString(job.get("last_run_at")));
            attrs.put("lastStatus", job.get("last_status"));
            attrs.put("nextRunAt", instantString(job.get("next_run_at")));
        } else {
            attrs.put("cron", null);
            attrs.put("timezone", null);
            attrs.put("active", null);
            attrs.put("lastRunAt", null);
            attrs.put("lastStatus", null);
            attrs.put("nextRunAt", null);
        }

        if (!SCHEDULED.equals(flowType)) {
            attrs.put("scheduleStatus", STATUS_NOT_SCHEDULED);
        } else if (jobOpt.isEmpty()) {
            attrs.put("scheduleStatus", STATUS_UNSYNCED);
            attrs.put("reason", "Flow flowType is SCHEDULED but no scheduled_job row is registered");
        } else if (jobOpt.get().get("next_run_at") == null) {
            attrs.put("scheduleStatus", STATUS_UNSYNCED);
            attrs.put("reason", "Scheduled job has no next_run_at (paused or stalled)");
        } else {
            attrs.put("scheduleStatus", STATUS_SYNCED);
        }

        return ResponseEntity.ok(JsonApiResponseBuilder.single("flow-schedule", flowId, attrs));
    }

    @GetMapping("/{flowId}/runs")
    public ResponseEntity<Map<String, Object>> getRuns(
            @PathVariable String flowId,
            @RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "Bad Request", "No tenant context"));
        }

        if (flowRepository.findFlowTypeForTenant(flowId, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int cappedLimit = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows =
                scheduledJobRepository.findRecentRunsForFlow(flowId, tenantId, cappedLimit);

        List<Map<String, Object>> runs = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", row.get("id"));
            r.put("status", row.get("status"));
            r.put("startedAt", instantString(row.get("started_at")));
            r.put("completedAt", instantString(row.get("completed_at")));
            r.put("durationMs", row.get("duration_ms"));
            r.put("errorMessage", row.get("error_message"));
            runs.add(r);
        }

        Map<String, Object> meta = Map.of("flowId", flowId, "count", runs.size());
        log.debug("Returned {} runs for flow {}", runs.size(), flowId);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("flow-runs", runs, meta));
    }

    private static String instantString(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toInstant().toString();
        return v.toString();
    }
}
