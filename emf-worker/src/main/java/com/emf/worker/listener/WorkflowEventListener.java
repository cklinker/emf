package com.emf.worker.listener;

import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that consumes record change events and triggers workflow evaluation.
 * <p>
 * Listens on the {@code emf.record.changed} topic with consumer group
 * {@code emf-worker-workflows}. All worker instances share this consumer group,
 * so each record change event is processed by exactly one worker.
 * <p>
 * This replaces the control-plane's {@code WorkflowEventListener}, moving workflow
 * evaluation into the worker for self-contained operation.
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(WorkflowEngine.class)
public class WorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventListener.class);

    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public WorkflowEventListener(WorkflowEngine workflowEngine, ObjectMapper objectMapper) {
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles record change events by evaluating workflow rules.
     * <p>
     * For each event, the workflow engine:
     * <ol>
     *   <li>Finds active rules matching the tenant, collection, and trigger type</li>
     *   <li>Evaluates filter formulas against the record data</li>
     *   <li>Executes matching actions via the action handler registry</li>
     *   <li>Logs execution results</li>
     * </ol>
     *
     * @param message the raw JSON Kafka message
     */
    @KafkaListener(
        topics = "emf.record.changed",
        groupId = "emf-worker-workflows",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRecordChanged(String message) {
        try {
            RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);

            log.debug("Received record change event: collection={}, recordId={}, changeType={}",
                event.getCollectionName(), event.getRecordId(), event.getChangeType());

            workflowEngine.evaluate(event);

        } catch (Exception e) {
            log.error("Error processing record change event for workflow evaluation: {}",
                e.getMessage(), e);
        }
    }
}
