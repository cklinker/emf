package com.emf.worker.listener;

import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.flow.FlowEngine;
import com.emf.runtime.flow.FlowTriggerEvaluator;
import com.emf.runtime.flow.InitialStateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka listener that consumes record change events and evaluates flow triggers.
 * <p>
 * Listens on the {@code emf.record.changed} topic with consumer group
 * {@code emf-worker-flows}. For each event, finds active RECORD_TRIGGERED flows
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
    @KafkaListener(
        topics = "emf.record.changed",
        groupId = "emf-worker-flows",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRecordChanged(String message) {
        try {
            RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);

            log.debug("Flow listener received record change: collection={}, recordId={}, changeType={}",
                    event.getCollectionName(), event.getRecordId(), event.getChangeType());

            List<FlowTriggerConfig> configs = getActiveFlowConfigs(event.getTenantId());
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
                                event.getTenantId(),
                                config.flowId(),
                                config.definitionJson(),
                                initialState,
                                event.getUserId(),
                                false);
                    }
                } catch (Exception e) {
                    log.error("Error evaluating flow trigger for flowId={}: {}",
                            config.flowId(), e.getMessage(), e);
                }
            }

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
     * Internal record holding a flow's trigger configuration.
     */
    record FlowTriggerConfig(
            String flowId,
            String tenantId,
            String definitionJson,
            Map<String, Object> triggerConfig
    ) {}
}
