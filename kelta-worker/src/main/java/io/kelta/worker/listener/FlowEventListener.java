package io.kelta.worker.listener;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.FlowTriggerEvaluator;
import io.kelta.runtime.flow.InitialStateBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka listener that consumes record change events and evaluates flow triggers.
 * <p>
 * Listens on the {@code kelta.record.changed} topic with consumer group
 * {@code kelta-worker-flows}. For each event, finds active RECORD_TRIGGERED flows
 * for the tenant, evaluates trigger conditions, and starts matching flow executions.
 * <p>
 * Maintains an in-memory cache of active flow trigger configs per tenant,
 * loaded from the DB on first access. The cache is invalidated when flows are
 * created, updated, or deleted.
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(FlowEngine.class)
public class FlowEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlowEventListener.class);

    private final FlowEngine flowEngine;
    private final FlowTriggerEvaluator triggerEvaluator;
    private final InitialStateBuilder initialStateBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Cache: tenantId → list of active flow trigger configs.
     * Invalidated via {@link #invalidateCache(String)}.
     */
    private final ConcurrentHashMap<String, List<FlowTriggerConfig>> triggerConfigCache =
            new ConcurrentHashMap<>();

    private static final String SELECT_ACTIVE_RECORD_FLOWS = """
            SELECT id, tenant_id, definition, trigger_config
            FROM flow
            WHERE tenant_id = ?
              AND flow_type = 'RECORD_TRIGGERED'
              AND active = true
            """;

    public FlowEventListener(FlowEngine flowEngine,
                              FlowTriggerEvaluator triggerEvaluator,
                              InitialStateBuilder initialStateBuilder,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper) {
        this.flowEngine = flowEngine;
        this.triggerEvaluator = triggerEvaluator;
        this.initialStateBuilder = initialStateBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles record change events by evaluating flow triggers.
     *
     * @param message the raw JSON Kafka message
     */
    public void handleRecordChanged(String message) {
        try {
            var tree = objectMapper.readTree(message);
            String tenantId = tree.path("tenantId").asText(null);
            if (tenantId == null || tenantId.isBlank()) {
                log.debug("Dropping flow event with no tenantId");
                return;
            }
            String userId = tree.path("userId").asText(null);
            Instant timestamp = parseTimestamp(tree.path("timestamp"));

            var payloadNode = tree.has("payload") ? tree.get("payload") : tree;
            RecordChangedPayload payload = objectMapper.treeToValue(payloadNode, RecordChangedPayload.class);

            // Reconstruct PlatformEvent envelope for downstream consumers
            PlatformEvent<RecordChangedPayload> event = new PlatformEvent<>(
                    tree.path("eventId").asText(null),
                    tree.path("eventType").asText(null),
                    tenantId,
                    tree.path("correlationId").asText(null),
                    userId,
                    timestamp,
                    payload
            );

            log.debug("Flow listener received record change: collection={}, recordId={}, changeType={}",
                    payload.getCollectionName(), payload.getRecordId(), payload.getChangeType());

            final String boundUserId = userId;
            TenantContext.runWithTenant(tenantId, () -> {
                List<FlowTriggerConfig> configs = getActiveFlowConfigs(tenantId);
                if (configs.isEmpty()) {
                    return;
                }

                for (FlowTriggerConfig config : configs) {
                    try {
                        if (triggerEvaluator.matchesRecordTrigger(event, config.triggerConfig())) {
                            String executionId = UUID.randomUUID().toString();
                            Map<String, Object> initialState = initialStateBuilder.buildFromRecordEvent(
                                    event, config.flowId(), executionId);

                            log.info("Starting flow execution: flowId={}, executionId={}, trigger=RECORD_CHANGE",
                                    config.flowId(), executionId);

                            flowEngine.startExecution(
                                    tenantId,
                                    config.flowId(),
                                    config.definitionJson(),
                                    initialState,
                                    boundUserId,
                                    false);
                        }
                    } catch (Exception e) {
                        log.error("Error evaluating flow trigger for flowId={}: {}",
                                config.flowId(), e.getMessage(), e);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Error processing record change event for flow evaluation: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Invalidates the trigger config cache for a tenant.
     * Called when flows are created, updated, or deleted.
     *
     * @param tenantId the tenant whose cache should be invalidated
     */
    public void invalidateCache(String tenantId) {
        triggerConfigCache.remove(tenantId);
        log.debug("Invalidated flow trigger cache for tenant {}", tenantId);
    }

    /**
     * Invalidates all cached trigger configs.
     */
    public void invalidateAllCaches() {
        triggerConfigCache.clear();
        log.debug("Invalidated all flow trigger caches");
    }

    @SuppressWarnings("unchecked")
    private List<FlowTriggerConfig> getActiveFlowConfigs(String tenantId) {
        return triggerConfigCache.computeIfAbsent(tenantId, tid -> {
            try {
                return jdbcTemplate.query(SELECT_ACTIVE_RECORD_FLOWS, (rs, rowNum) -> {
                    String flowId = rs.getString("id");
                    String definitionJson = rs.getString("definition");
                    String triggerConfigJson = rs.getString("trigger_config");

                    Map<String, Object> triggerConfig = Map.of();
                    if (triggerConfigJson != null && !triggerConfigJson.isBlank()) {
                        try {
                            triggerConfig = objectMapper.readValue(triggerConfigJson, Map.class);
                            // Defensive unwrap: trigger_config may be stored in a JDBC/jsonb
                            // wrapper format like {"null":false,"type":"jsonb","value":"{...}"}.
                            // If so, extract and re-parse the inner JSON from the "value" key.
                            if ("jsonb".equals(triggerConfig.get("type"))
                                    && triggerConfig.containsKey("value")) {
                                Object innerValue = triggerConfig.get("value");
                                if (innerValue instanceof String innerJson) {
                                    triggerConfig = objectMapper.readValue(innerJson, Map.class);
                                    log.debug("Unwrapped jsonb wrapper for flow {} trigger_config", flowId);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse trigger_config for flow {}: {}", flowId, e.getMessage());
                        }
                    }

                    return new FlowTriggerConfig(flowId, tid, definitionJson, triggerConfig);
                }, tid);
            } catch (Exception e) {
                log.error("Failed to load active flow configs for tenant {}: {}", tid, e.getMessage(), e);
                return List.of();
            }
        });
    }

    /**
     * Parses a timestamp from a JSON node, handling both ISO-8601 strings
     * and numeric epoch-seconds values (as produced by Jackson's default
     * Instant serialization).
     */
    private static Instant parseTimestamp(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.longValue());
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            // Try parsing as a numeric string (e.g., "1.772421171E9")
            try {
                double epochSeconds = Double.parseDouble(text);
                return Instant.ofEpochSecond((long) epochSeconds);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }

    /**
     * Internal record holding a flow's trigger configuration.
     */
    record FlowTriggerConfig(
            String flowId,
            String tenantId,
            String definitionJson,
            Map<String, Object> triggerConfig
    ) {}
}
