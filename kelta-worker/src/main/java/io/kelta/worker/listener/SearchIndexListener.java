package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CollectionLifecycleManager;
import io.kelta.worker.service.SearchIndexService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka listener that consumes record change events and updates the full-text search index.
 *
 * <p>Listens on the {@code kelta.record.changed} topic with consumer group
 * {@code kelta-worker-search-index}. For each event, builds search content from
 * the record's searchable fields and upserts/deletes from the {@code search_index} table.
 *
 * <p>Uses a separate consumer group from {@code FlowEventListener} to ensure
 * search indexing is independent and does not interfere with flow processing.
 *
 * @since 1.0.0
 */
@Component
public class SearchIndexListener {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexListener.class);

    private final SearchIndexService searchIndexService;
    private final CollectionLifecycleManager lifecycleManager;
    private final CollectionRegistry collectionRegistry;
    private final ObjectMapper objectMapper;

    public SearchIndexListener(SearchIndexService searchIndexService,
                                CollectionLifecycleManager lifecycleManager,
                                CollectionRegistry collectionRegistry,
                                ObjectMapper objectMapper) {
        this.searchIndexService = searchIndexService;
        this.lifecycleManager = lifecycleManager;
        this.collectionRegistry = collectionRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles record change events by updating the search index.
     *
     * @param message the raw JSON Kafka message
     */
    @KafkaListener(
        topics = "kelta.record.changed",
        groupId = "kelta-worker-search-index",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRecordChanged(String message) {
        try {
            var tree = objectMapper.readTree(message);
            String tenantId = tree.path("tenantId").asText(null);

            if (tenantId == null || tenantId.isBlank()) {
                log.debug("Skipping search index update: no tenantId in event");
                return;
            }

            var payloadNode = tree.has("payload") ? tree.get("payload") : tree;
            RecordChangedPayload payload = objectMapper.treeToValue(
                    payloadNode, RecordChangedPayload.class);

            String collectionName = payload.getCollectionName();
            String recordId = payload.getRecordId();
            ChangeType changeType = payload.getChangeType();

            if (collectionName == null || recordId == null || changeType == null) {
                log.debug("Skipping search index update: incomplete payload");
                return;
            }

            // Skip system collection changes (fields, collections, etc.)
            if (isSystemCollection(collectionName)) {
                return;
            }

            TenantContext.set(tenantId);
            try {
                switch (changeType) {
                    case CREATED, UPDATED -> {
                        Map<String, Object> data = payload.getData();
                        if (data != null && !data.isEmpty()) {
                            String collectionId = lifecycleManager.getCollectionIdByName(collectionName);
                            if (collectionId != null) {
                                searchIndexService.indexRecord(
                                        tenantId, collectionId, collectionName, recordId, data);
                            }
                        }
                    }
                    case DELETED -> searchIndexService.removeRecord(tenantId, collectionName, recordId);
                }
            } finally {
                TenantContext.clear();
            }

        } catch (Exception e) {
            log.error("Error processing record change for search index: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns true if the collection is a system collection that should not be indexed.
     * Uses the {@code systemCollection} flag from the {@link CollectionRegistry} instead
     * of a hardcoded list, so newly added system collections are automatically excluded.
     */
    private boolean isSystemCollection(String collectionName) {
        CollectionDefinition definition = collectionRegistry.get(collectionName);
        if (definition == null) {
            // Unknown collections are likely system collections not yet registered;
            // err on the side of not indexing them
            return true;
        }
        return definition.systemCollection();
    }
}
