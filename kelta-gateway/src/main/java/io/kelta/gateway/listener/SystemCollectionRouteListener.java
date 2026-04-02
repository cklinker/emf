package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import io.kelta.gateway.route.RouteRegistry;
import io.kelta.runtime.event.RecordChangedPayload;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that handles record change events for system collections.
 *
 * <p>When system collection records are modified through the worker's
 * {@code DynamicCollectionRouter}, this listener:
 * <ol>
 *   <li>Refreshes gateway routes when {@code collections} records change</li>
 *   <li>Refreshes governor limit cache when {@code tenants} records change</li>
 * </ol>
 *
 * <p>Permission cache eviction is no longer needed — Cerbos policies are
 * synced directly from the worker when profiles change.
 *
 * @since 1.0.0
 */
@Component
public class SystemCollectionRouteListener {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionRouteListener.class);

    private final RouteRegistry routeRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final GatewayCacheManager cacheManager;

    public SystemCollectionRouteListener(RouteRegistry routeRegistry,
                                          ApplicationEventPublisher applicationEventPublisher,
                                          ObjectMapper objectMapper,
                                          GatewayCacheManager cacheManager) {
        this.routeRegistry = routeRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    @KafkaListener(
            topics = "${kelta.gateway.kafka.topics.record-changed:kelta.record.changed}",
            groupId = "${spring.kafka.consumer.group-id}-record",
            containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    public void onRecordChanged(String message) {
        try {
            var tree = objectMapper.readTree(message);

            var payloadNode = tree.has("payload") ? tree.get("payload") : tree;
            RecordChangedPayload payload = objectMapper.treeToValue(payloadNode, RecordChangedPayload.class);

            String collectionName = payload.getCollectionName();

            if (collectionName == null) {
                return;
            }

            if ("collections".equals(collectionName)) {
                log.info("Collection definition changed (recordId={}, changeType={}), refreshing routes",
                        payload.getRecordId(), payload.getChangeType());
                applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            }

            if ("tenants".equals(collectionName)) {
                log.info("Tenant record changed (recordId={}, changeType={}), refreshing governor limits",
                        payload.getRecordId(), payload.getChangeType());
                cacheManager.refreshGovernorLimitsFromWorker();
            }

            if ("tenant_custom_domains".equals(collectionName)) {
                String domain = payload.getData() != null
                        ? (String) payload.getData().get("domain") : null;
                log.info("Custom domain changed (recordId={}, changeType={}, domain={}), evicting domain cache",
                        payload.getRecordId(), payload.getChangeType(), domain);
                if (domain != null) {
                    cacheManager.removeCustomDomain(domain);
                } else {
                    // If we can't identify the specific domain, evict all custom domain entries
                    cacheManager.evictAllCustomDomains();
                }
            }

        } catch (Exception e) {
            log.error("Error processing record change event: {}", e.getMessage(), e);
        }
    }
}
