package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Subscribes to {@code kelta.config.layout.changed.*} (broadcast) and evicts
 * the worker system-collection cache for the page-layout family on every pod.
 *
 * <p>The event envelope carries the tenant id at the top level; the per-tenant
 * cache entries for the five layout collections are dropped on receipt.
 */
@Component
public class LayoutCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(LayoutCacheInvalidationListener.class);

    private static final List<String> LAYOUT_FAMILY = List.of(
            "page-layouts",
            "layout-sections",
            "layout-fields",
            "layout-related-lists",
            "layout-rules");

    private final WorkerCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public LayoutCacheInvalidationListener(WorkerCacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    public void handleLayoutChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode tenantNode = root.get("tenantId");
            String tenantId = tenantNode != null && !tenantNode.isNull() && tenantNode.isTextual()
                    ? tenantNode.stringValue()
                    : null;

            log.info("Layout config changed (tenantId={}) — evicting worker cache for layout family", tenantId);
            for (String collection : LAYOUT_FAMILY) {
                cacheManager.evictSystemCollection(tenantId, collection);
            }
        } catch (Exception e) {
            log.error("Failed to process layout-changed event: {}", e.getMessage(), e);
        }
    }
}
