package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.PendingActionStore;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Action handler that delays subsequent workflow actions.
 *
 * <p>Config format:
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
 *
 * <p>Uses {@link PendingActionStore} SPI to persist pending actions.
 * A separate executor polls for due pending actions and resumes the workflow.
 *
 * @since 1.0.0
 */
public class DelayActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(DelayActionHandler.class);

    private final ObjectMapper objectMapper;
    private final PendingActionStore pendingActionStore;

    public DelayActionHandler(ObjectMapper objectMapper, PendingActionStore pendingActionStore) {
        this.objectMapper = objectMapper;
        this.pendingActionStore = pendingActionStore;
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

            // Snapshot the record data for resumption
            String recordSnapshot = null;
            if (context.data() != null && !context.data().isEmpty()) {
                recordSnapshot = objectMapper.writeValueAsString(context.data());
            }

            String pendingActionId = pendingActionStore.save(
                context.tenantId(),
                context.executionLogId() != null ? context.executionLogId() : "",
                context.workflowRuleId(),
                0, // Will be set by engine when resuming
                context.recordId(),
                scheduledAt,
                recordSnapshot
            );

            log.info("Delay action created: pending={}, scheduledAt={}, rule={}, record={}",
                pendingActionId, scheduledAt, context.workflowRuleId(), context.recordId());

            return ActionResult.success(Map.of(
                "pendingActionId", pendingActionId,
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

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
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
