package com.emf.runtime.flow;

import com.emf.runtime.event.RecordChangeEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Constructs the canonical initial state envelope for flow executions.
 * <p>
 * Every flow execution starts with a state object following a common structure:
 * <pre>
 * {
 *   "trigger": { "type": "...", ...metadata... },
 *   "&lt;source-key&gt;": { ...source data... },
 *   "context": { "tenantId": "...", "flowId": "...", "executionId": "..." }
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class InitialStateBuilder {

    /**
     * Builds initial state from a record change event.
     *
     * @param event       the record change event
     * @param flowId      the flow being executed
     * @param executionId the execution ID
     * @return the initial state envelope
     */
    public Map<String, Object> buildFromRecordEvent(RecordChangeEvent event,
                                                     String flowId, String executionId) {
        Map<String, Object> state = new LinkedHashMap<>();

        // Trigger metadata
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "RECORD_CHANGE");
        trigger.put("changeType", event.getChangeType().name());
        trigger.put("collectionName", event.getCollectionName());
        trigger.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);
        state.put("trigger", trigger);

        // Record data
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", event.getRecordId());
        record.put("collectionName", event.getCollectionName());
        record.put("data", event.getData() != null ? event.getData() : Map.of());
        record.put("previousData", event.getPreviousData() != null ? event.getPreviousData() : Map.of());
        record.put("changedFields", event.getChangedFields() != null ? event.getChangedFields() : java.util.List.of());
        state.put("record", record);

        // Context
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenantId", event.getTenantId());
        context.put("userId", event.getUserId());
        context.put("flowId", flowId);
        context.put("executionId", executionId);
        state.put("context", context);

        return state;
    }

    /**
     * Builds initial state from an API invocation.
     *
     * @param inputPayload the caller-provided input
     * @param tenantId     the tenant ID
     * @param userId       the user ID
     * @param flowId       the flow being executed
     * @param executionId  the execution ID
     * @return the initial state envelope
     */
    public Map<String, Object> buildFromApiInvocation(Map<String, Object> inputPayload,
                                                       String tenantId, String userId,
                                                       String flowId, String executionId) {
        Map<String, Object> state = new LinkedHashMap<>();

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "API_INVOCATION");
        state.put("trigger", trigger);

        state.put("input", inputPayload != null ? inputPayload : Map.of());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenantId", tenantId);
        context.put("userId", userId);
        context.put("flowId", flowId);
        context.put("executionId", executionId);
        state.put("context", context);

        return state;
    }

    /**
     * Builds initial state from a scheduled execution.
     *
     * @param triggerConfig the flow's trigger config (may contain inputData)
     * @param tenantId      the tenant ID
     * @param flowId        the flow being executed
     * @param executionId   the execution ID
     * @return the initial state envelope
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildFromSchedule(Map<String, Object> triggerConfig,
                                                  String tenantId,
                                                  String flowId, String executionId) {
        Map<String, Object> state = new LinkedHashMap<>();

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "SCHEDULED");
        state.put("trigger", trigger);

        // Static input data from trigger config
        Map<String, Object> inputData = triggerConfig != null
            ? (Map<String, Object>) triggerConfig.getOrDefault("inputData", Map.of())
            : Map.of();
        state.put("input", inputData);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenantId", tenantId);
        context.put("flowId", flowId);
        context.put("executionId", executionId);
        state.put("context", context);

        return state;
    }
}
