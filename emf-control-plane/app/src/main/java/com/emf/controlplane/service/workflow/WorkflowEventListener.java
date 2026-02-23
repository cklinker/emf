package com.emf.controlplane.service.workflow;

import com.emf.controlplane.tenant.TenantContextHolder;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Kafka listener that consumes record change events and delegates to the
 * {@link WorkflowEngine} for rule evaluation and action execution.
 * <p>
 * Supports batch processing — receives multiple messages per poll and processes
 * each event on the dedicated workflow executor thread pool to prevent
 * blocking the Kafka consumer thread.
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
    private final Executor workflowExecutor;

    public WorkflowEventListener(WorkflowEngine workflowEngine,
                                  ObjectMapper objectMapper,
                                  @Qualifier("workflowExecutor") @Nullable Executor workflowExecutor) {
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
        this.workflowExecutor = workflowExecutor;
    }

    @KafkaListener(
        topics = "${emf.control-plane.kafka.topics.record-changed:emf.record.changed}",
        groupId = "${emf.control-plane.kafka.group-id:emf-control-plane}",
        containerFactory = "workflowKafkaListenerContainerFactory",
        batch = "true"
    )
    public void onRecordChanged(List<String> messages) {
        log.debug("Received batch of {} record change events", messages.size());
        for (String message : messages) {
            processEvent(message);
        }
    }

    /**
     * Processes a single record change event. If a workflow executor is available,
     * submits the work to the dedicated thread pool; otherwise runs inline.
     */
    private void processEvent(String message) {
        Runnable task = () -> {
            try {
                RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);
                log.debug("Processing record change event: collection={}, recordId={}, changeType={}",
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
        };

        if (workflowExecutor != null) {
            workflowExecutor.execute(task);
        } else {
            task.run();
        }
    }
}
