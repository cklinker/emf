package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.WorkflowPendingAction;
import com.emf.controlplane.repository.WorkflowPendingActionRepository;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Action handler that delays subsequent workflow actions.
 * <p>
 * Config format:
 * <pre>
 * // Delay by duration:
 * { "delayMinutes": 60 }
 *
 * // Delay until a specific field's date/time value:
 * { "delayUntilField": "dueDate" }
 *
 * // Delay until a fixed time:
 * { "delayUntilTime": "2024-12-31T23:59:59" }
 * </pre>
 * <p>
 * Creates a {@link WorkflowPendingAction} record with the computed scheduled time.
 * A separate executor polls for due pending actions and resumes the workflow.
 */
@Component
public class DelayActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(DelayActionHandler.class);

    private final ObjectMapper objectMapper;
    private final WorkflowPendingActionRepository pendingActionRepository;

    public DelayActionHandler(ObjectMapper objectMapper,
                               WorkflowPendingActionRepository pendingActionRepository) {
        this.objectMapper = objectMapper;
        this.pendingActionRepository = pendingActionRepository;
    }

    @Override
    public String getActionTypeKey() {
        return "DELAY";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            Instant scheduledAt = computeScheduledTime(config, context);
            if (scheduledAt == null) {
                return ActionResult.failure("Could not compute delay time from config");
            }

            // Create pending action record
            WorkflowPendingAction pending = new WorkflowPendingAction();
            pending.setTenantId(context.tenantId());
            pending.setExecutionLogId(context.executionLogId() != null ? context.executionLogId() : "");
            pending.setWorkflowRuleId(context.workflowRuleId());
            pending.setActionIndex(0); // Will be set by engine when resuming
            pending.setRecordId(context.recordId());
            pending.setScheduledAt(scheduledAt);
            pending.setStatus("PENDING");

            // Snapshot the record data for resumption
            if (context.data() != null && !context.data().isEmpty()) {
                pending.setRecordSnapshot(objectMapper.writeValueAsString(context.data()));
            }

            pendingActionRepository.save(pending);

            log.info("Delay action created: pending={}, scheduledAt={}, rule={}, record={}",
                pending.getId(), scheduledAt, context.workflowRuleId(), context.recordId());

            return ActionResult.success(Map.of(
                "pendingActionId", pending.getId(),
                "scheduledAt", scheduledAt.toString(),
                "status", "PENDING"
            ));
        } catch (Exception e) {
            log.error("Failed to execute delay action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    /**
     * Computes the scheduled execution time from the config.
     */
    Instant computeScheduledTime(Map<String, Object> config, ActionContext context) {
        // Option 1: delayMinutes
        Object delayMinutes = config.get("delayMinutes");
        if (delayMinutes != null) {
            int minutes = delayMinutes instanceof Number
                ? ((Number) delayMinutes).intValue()
                : Integer.parseInt(delayMinutes.toString());
            return Instant.now().plus(minutes, ChronoUnit.MINUTES);
        }

        // Option 2: delayUntilField — read date from record data
        String delayUntilField = (String) config.get("delayUntilField");
        if (delayUntilField != null && !delayUntilField.isBlank()) {
            Object fieldValue = context.data() != null ? context.data().get(delayUntilField) : null;
            if (fieldValue == null) {
                log.warn("delayUntilField '{}' not found or null in record data", delayUntilField);
                return null;
            }
            return parseInstant(fieldValue.toString());
        }

        // Option 3: delayUntilTime — fixed datetime
        String delayUntilTime = (String) config.get("delayUntilTime");
        if (delayUntilTime != null && !delayUntilTime.isBlank()) {
            return parseInstant(delayUntilTime);
        }

        return null;
    }

    /**
     * Parses a date/time string into an Instant.
     */
    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            // Try parsing as LocalDateTime
            try {
                LocalDateTime ldt = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse date/time value: {}", value);
                return null;
            }
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            boolean hasDelayMinutes = config.containsKey("delayMinutes");
            boolean hasDelayUntilField = config.containsKey("delayUntilField");
            boolean hasDelayUntilTime = config.containsKey("delayUntilTime");

            if (!hasDelayMinutes && !hasDelayUntilField && !hasDelayUntilTime) {
                throw new IllegalArgumentException(
                    "Config must contain one of: 'delayMinutes', 'delayUntilField', or 'delayUntilTime'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
