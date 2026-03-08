package io.kelta.gateway.listener;

import io.kelta.gateway.authz.PermissionResolutionService;
import io.kelta.gateway.cache.GatewayCacheManager;
import io.kelta.gateway.route.RouteRegistry;
import io.kelta.runtime.event.RecordChangedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Kafka listener that handles record change events for system collections.
 *
 * <p>When system collection records are modified through the worker's
 * {@code DynamicCollectionRouter}, this listener:
 * <ol>
 *   <li>Refreshes gateway routes when {@code collections} records change</li>
 *   <li>Refreshes governor limit cache when {@code tenants} records change</li>
 *   <li>Evicts permission cache when permission-related collections change</li>
 * </ol>
 *
 * <p>This listener consumes from the {@code kelta.record.changed} topic using the
 * {@code recordEventKafkaListenerContainerFactory} (StringDeserializer) because
 * the worker serializes events as JSON strings.
 *
 * <p>Uses a different consumer group ({@code kelta-gateway-record}) from the
 * existing {@link ConfigEventListener} so both listeners receive all messages
 * independently.
 *
 * @since 1.0.0
 */
@Component
public class SystemCollectionRouteListener {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionRouteListener.class);

    /** Collections whose changes should trigger permission cache eviction. */
    private static final Set<String> PERMISSION_COLLECTIONS = Set.of(
            "profiles", "permission-sets",
            "profile-system-permissions", "profile-object-permissions", "profile-field-permissions",
            "permset-system-permissions", "permset-object-permissions", "permset-field-permissions",
            "user-permission-sets", "group-permission-sets",
            "user-groups", "group-memberships", "users"
    );

    private final RouteRegistry routeRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final PermissionResolutionService permissionResolutionService;
    private final GatewayCacheManager cacheManager;

    public SystemCollectionRouteListener(RouteRegistry routeRegistry,
                                          ApplicationEventPublisher applicationEventPublisher,
                                          ObjectMapper objectMapper,
                                          @Nullable PermissionResolutionService permissionResolutionService,
                                          GatewayCacheManager cacheManager) {
        this.routeRegistry = routeRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.permissionResolutionService = permissionResolutionService;
        this.cacheManager = cacheManager;
    }

    /**
     * Handles record change events from the worker.
     * Refreshes routes and evicts permission cache as needed.
     */
    @KafkaListener(
            topics = "${kelta.gateway.kafka.topics.record-changed:kelta.record.changed}",
            groupId = "${spring.kafka.consumer.group-id}-record",
            containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    public void onRecordChanged(String message) {
        try {
            var tree = objectMapper.readTree(message);
            String tenantId = tree.path("tenantId").asText(null);

            var payloadNode = tree.has("payload") ? tree.get("payload") : tree;
            RecordChangedPayload payload = objectMapper.treeToValue(payloadNode, RecordChangedPayload.class);

            String collectionName = payload.getCollectionName();

            if (collectionName == null) {
                return;
            }

            // If a collection definition changed, refresh gateway routes
            if ("collections".equals(collectionName)) {
                log.info("Collection definition changed (recordId={}, changeType={}), refreshing routes",
                        payload.getRecordId(), payload.getChangeType());
                applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            }

            // Refresh governor limit cache when tenant records change
            if ("tenants".equals(collectionName)) {
                log.info("Tenant record changed (recordId={}, changeType={}), refreshing governor limits",
                        payload.getRecordId(), payload.getChangeType());
                cacheManager.refreshGovernorLimitsFromWorker();
            }

            // Evict permission cache when permission-related collections change
            if (PERMISSION_COLLECTIONS.contains(collectionName) && permissionResolutionService != null) {
                if (tenantId != null) {
                    permissionResolutionService.evictPermissionCache(tenantId)
                            .doOnSuccess(v -> log.info(
                                    "Evicted permission cache for tenant {} due to {} change",
                                    tenantId, collectionName))
                            .subscribe();
                }
            }

        } catch (Exception e) {
            log.error("Error processing record change event: {}", e.getMessage(), e);
        }
    }
}
