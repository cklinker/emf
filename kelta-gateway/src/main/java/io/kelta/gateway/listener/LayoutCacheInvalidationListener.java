package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Subscribes to {@code kelta.config.layout.changed.*} (broadcast) and evicts
 * the gateway response cache for the page-layout family of system collections.
 *
 * <p>Edits to any child of a layout — sections, fields, related-lists, rules —
 * change the rendered payload of {@code GET /api/page-layouts/{id}?include=...}
 * even though the URL path resolves to {@code page-layouts}. The default
 * mutation-driven eviction in {@code SystemCollectionResponseCacheFilter}
 * only drops the directly-mutated collection (e.g. {@code layout-related-lists}),
 * leaving cached parent {@code page-layouts} responses stale for up to 10
 * minutes. This listener bridges that gap.
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

    private final GatewayCacheManager cacheManager;

    public LayoutCacheInvalidationListener(GatewayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void handleLayoutChanged(String message) {
        try {
            log.info("Layout config changed — evicting gateway response cache for layout family");
            for (String collection : LAYOUT_FAMILY) {
                cacheManager.evictSystemCollectionResponses(collection);
            }
        } catch (Exception e) {
            log.error("Failed to process layout-changed event: {}", e.getMessage(), e);
        }
    }
}
