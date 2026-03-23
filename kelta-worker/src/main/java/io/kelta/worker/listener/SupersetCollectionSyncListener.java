package io.kelta.worker.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.worker.service.SupersetDatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka listener that syncs Superset datasets when collections are
 * created or updated.
 *
 * <p>Consumes from the {@code kelta.config.collection.changed} topic
 * and creates/updates the corresponding Superset dataset.
 *
 * @since 1.0.0
 */
public class SupersetCollectionSyncListener {

    private static final Logger log = LoggerFactory.getLogger(SupersetCollectionSyncListener.class);

    private final SupersetDatasetService datasetService;
    private final ObjectMapper objectMapper;

    public SupersetCollectionSyncListener(SupersetDatasetService datasetService,
                                           ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kelta.kafka.topics.collection-changed:kelta.config.collection.changed}",
            groupId = "kelta-worker-superset-datasets"
    )
    public void onCollectionChanged(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String changeType = event.path("changeType").asText();

            // Only sync on CREATED or UPDATED events
            if (!"CREATED".equals(changeType) && !"UPDATED".equals(changeType)) {
                return;
            }

            String tenantId = event.path("tenantId").asText(null);
            String tenantSlug = event.path("tenantSlug").asText(null);
            String collectionId = event.path("collectionId").asText(null);
            String collectionName = event.path("collectionName").asText(null);

            if (tenantId == null || tenantSlug == null || collectionId == null || collectionName == null) {
                log.debug("Incomplete collection change event — skipping Superset dataset sync");
                return;
            }

            log.info("Syncing Superset dataset for collection '{}' in tenant '{}'",
                    collectionName, tenantSlug);
            datasetService.syncDatasetForCollection(tenantId, tenantSlug, collectionId, collectionName);

        } catch (Exception e) {
            log.error("Failed to process collection change event for Superset sync: {}",
                    e.getMessage());
        }
    }
}
