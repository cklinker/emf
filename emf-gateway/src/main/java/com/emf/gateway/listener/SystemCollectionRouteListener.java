package com.emf.gateway.listener;

import com.emf.gateway.authz.PermissionResolutionService;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
 *   <li>Invalidates Redis cache for include resolution when any record changes</li>
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
    private static final String REDIS_KEY_PREFIX = "jsonapi:";

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
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PermissionResolutionService permissionResolutionService;

    public SystemCollectionRouteListener(RouteRegistry routeRegistry,
                                          ApplicationEventPublisher applicationEventPublisher,
                                          ReactiveRedisTemplate<String, String> redisTemplate,
                                          ObjectMapper objectMapper,
                                          @Nullable PermissionResolutionService permissionResolutionService) {
        this.routeRegistry = routeRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.permissionResolutionService = permissionResolutionService;
    }

    /**
     * Handles record change events from the worker.
     * Refreshes routes, invalidates caches, and evicts permission cache as needed.
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

            // Invalidate IncludeResolver Redis cache for the changed record
            invalidateRedisCache(collectionName, event.getRecordId());

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

    /**
     * Invalidates the Redis cache entry for a specific resource.
     * The IncludeResolver caches resources at key {@code jsonapi:{type}:{id}}.
     */
    private void invalidateRedisCache(String collectionName, String recordId) {
        if (recordId == null) {
            return;
        }

        String redisKey = REDIS_KEY_PREFIX + collectionName + ":" + recordId;
        redisTemplate.delete(redisKey)
                .doOnSuccess(deleted -> {
                    if (deleted != null && deleted > 0) {
                        log.debug("Invalidated Redis cache for resource: {}", redisKey);
                    }
                })
                .doOnError(error -> {
                    log.warn("Failed to invalidate Redis cache for {}: {}", redisKey, error.getMessage());
                })
                .subscribe();
    }
}
