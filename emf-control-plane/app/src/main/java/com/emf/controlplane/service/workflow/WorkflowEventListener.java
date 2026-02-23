package com.emf.controlplane.service.workflow;

import com.emf.controlplane.tenant.TenantContextHolder;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that consumes record change events and delegates to the
 * {@link WorkflowEngine} for rule evaluation and action execution.
 * <p>
 * Listens on the {@code emf.record.changed} topic. Each message contains a
 * {@link RecordChangeEvent} with the tenant, collection, record data, and change type.
 * <p>
 * Sets the {@link TenantContextHolder} for each event so that tenant-scoped
 * database queries work correctly during workflow evaluation.
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class WorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventListener.class);

    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public WorkflowEventListener(WorkflowEngine workflowEngine, ObjectMapper objectMapper) {
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${emf.control-plane.kafka.topics.record-changed:emf.record.changed}",
        groupId = "${emf.control-plane.kafka.group-id:emf-control-plane}",
        containerFactory = "workflowKafkaListenerContainerFactory"
    )
    public void onRecordChanged(String message) {
        try {
            RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);
            log.debug("Received record change event: collection={}, recordId={}, changeType={}",
                event.getCollectionName(), event.getRecordId(), event.getChangeType());

            // Set tenant context for tenant-scoped database queries
            TenantContextHolder.set(event.getTenantId(), null);
            try {
                workflowEngine.evaluate(event);
            } finally {
                TenantContextHolder.clear();
            }
        } catch (Exception e) {
            log.error("Error processing record change event: {}", e.getMessage(), e);
        }
    }
}
