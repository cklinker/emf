package com.emf.controlplane.event;

import com.emf.controlplane.config.CacheConfig;
import com.emf.runtime.event.ConfigEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka listener for workflow rule change events.
 * <p>
 * When a workflow rule is created, updated, or deleted, the control-plane
 * publishes a {@code config.workflow.changed} event. This listener consumes
 * the event and evicts the workflow rules cache for the affected
 * tenant + collection, ensuring that the next evaluation fetches fresh
 * rules from the database.
 * <p>
 * This component is only active when Kafka is enabled.
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class WorkflowConfigListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigListener.class);

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public WorkflowConfigListener(CacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${emf.control-plane.kafka.topics.workflow-rule-changed:config.workflow.changed}",
        groupId = "${spring.kafka.consumer.group-id:emf-control-plane}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @SuppressWarnings("unchecked")
    public void handleWorkflowRuleChanged(ConfigEvent<Object> event) {
        try {
            log.info("Received workflow rule changed event: eventId={}, correlationId={}",
                    event.getEventId(), event.getCorrelationId());

            Object rawPayload = event.getPayload();
            if (rawPayload == null) {
                log.warn("Workflow rule changed event has null payload: eventId={}", event.getEventId());
                return;
            }

            // Convert payload to map for flexible access
            Map<String, Object> payload;
            if (rawPayload instanceof Map) {
                payload = (Map<String, Object>) rawPayload;
            } else {
                payload = objectMapper.convertValue(rawPayload,
                        new TypeReference<Map<String, Object>>() {});
            }

            String tenantId = (String) payload.get("tenantId");
            String collectionId = (String) payload.get("collectionId");
            String ruleId = (String) payload.get("ruleId");
            String changeType = payload.get("changeType") != null
                    ? payload.get("changeType").toString() : "UNKNOWN";

            log.info("Processing workflow rule change: ruleId={}, tenantId={}, collectionId={}, changeType={}",
                    ruleId, tenantId, collectionId, changeType);

            // Evict workflow rules cache for the affected tenant + collection
            evictWorkflowRulesCache(tenantId, collectionId);

        } catch (Exception e) {
            log.error("Error processing workflow rule changed event: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Evicts workflow rules cache entries for the given tenant and collection.
     * Cache key format: {@code {tenantId}:{collectionId}}
     */
    private void evictWorkflowRulesCache(String tenantId, String collectionId) {
        Cache cache = cacheManager.getCache(CacheConfig.WORKFLOW_RULES_CACHE);
        if (cache != null) {
            if (tenantId != null && collectionId != null) {
                String cacheKey = tenantId + ":" + collectionId;
                cache.evict(cacheKey);
                log.info("Evicted workflow rules cache: key={}", cacheKey);
            } else {
                // If we can't determine the specific key, clear the entire cache
                cache.clear();
                log.info("Cleared entire workflow rules cache (missing tenant/collection context)");
            }
        }
    }
}
