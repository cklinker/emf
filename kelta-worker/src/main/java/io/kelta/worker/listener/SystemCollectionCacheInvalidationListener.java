package io.kelta.worker.listener;

import io.kelta.runtime.model.system.SystemCollectionDefinitions;
import io.kelta.runtime.router.SystemCollectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Subscribes to {@code kelta.record.changed.>} (broadcast) and evicts the changed
 * system collection's response entries from the {@link SystemCollectionCache} on
 * every pod.
 *
 * <p>Without this, a system-collection write only evicted the cache on the pod
 * that handled the request ({@code DynamicCollectionRouter}'s same-pod eviction)
 * — the other replicas kept serving the stale cached JSON:API response until the
 * 10-minute TTL, and the gateway would then re-cache that stale body for another
 * 10 minutes. User-visible as field config (e.g. a picklist binding) not showing
 * up in the admin UI until ~20 minutes after the change.
 *
 * <p>User-collection record events vastly outnumber system ones on this subject,
 * so events are filtered against the static system-collection name set before
 * touching the cache.
 */
@Component
public class SystemCollectionCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionCacheInvalidationListener.class);

    private static final Set<String> SYSTEM_COLLECTION_NAMES =
            SystemCollectionDefinitions.byName().keySet();

    private final SystemCollectionCache systemCollectionCache;
    private final ObjectMapper objectMapper;

    public SystemCollectionCacheInvalidationListener(SystemCollectionCache systemCollectionCache,
                                                     ObjectMapper objectMapper) {
        this.systemCollectionCache = systemCollectionCache;
        this.objectMapper = objectMapper;
    }

    public void handleRecordChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;

            String collectionName = textOrNull(payload.get("collectionName"));
            if (collectionName == null || !SYSTEM_COLLECTION_NAMES.contains(collectionName)) {
                return;
            }

            String tenantId = textOrNull(root.get("tenantId"));
            if (tenantId == null) {
                tenantId = textOrNull(payload.get("tenantId"));
            }

            log.info("System collection '{}' changed (tenant {}) — evicting cached responses",
                    collectionName, tenantId);
            systemCollectionCache.evict(tenantId, collectionName);
            if (tenantId != null) {
                // Non-tenant-scoped reads cache under the "_" key — evict those too.
                systemCollectionCache.evict(null, collectionName);
            }
        } catch (Exception e) {
            log.error("Failed to process record changed event for cache eviction: {}", e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.stringValue();
        return value.isBlank() ? null : value;
    }
}
