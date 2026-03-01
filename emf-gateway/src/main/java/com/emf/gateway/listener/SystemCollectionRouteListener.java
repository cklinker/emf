package com.emf.gateway.listener;

import com.emf.gateway.authz.PermissionResolutionService;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.RecordChangeEvent;
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
 *   <li>Evicts permission cache when permission-related collections change</li>
 * </ol>
 *
 * <p>This listener consumes from the {@code emf.record.changed} topic using the
 * {@code recordEventKafkaListenerContainerFactory} (StringDeserializer) because
 * the worker serializes {@link RecordChangeEvent} as JSON strings.
 *
 * <p>Uses a different consumer group ({@code emf-gateway-record}) from the
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

    public SystemCollectionRouteListener(RouteRegistry routeRegistry,
                                          ApplicationEventPublisher applicationEventPublisher,
                                          ObjectMapper objectMapper,
                                          @Nullable PermissionResolutionService permissionResolutionService) {
        this.routeRegistry = routeRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.permissionResolutionService = permissionResolutionService;
    }

    /**
     * Handles record change events from the worker.
     * Refreshes routes and evicts permission cache as needed.
     */
    @KafkaListener(
            topics = "${emf.gateway.kafka.topics.record-changed:emf.record.changed}",
            groupId = "${spring.kafka.consumer.group-id}-record",
            containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    public void onRecordChanged(String message) {
        try {
            RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);
            String collectionName = event.getCollectionName();

            if (collectionName == null) {
                return;
            }

            // If a collection definition changed, refresh gateway routes
            if ("collections".equals(collectionName)) {
                log.info("Collection definition changed (recordId={}, changeType={}), refreshing routes",
                        event.getRecordId(), event.getChangeType());
                applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            }

            // Evict permission cache when permission-related collections change
            if (PERMISSION_COLLECTIONS.contains(collectionName) && permissionResolutionService != null) {
                String tenantId = event.getTenantId();
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
