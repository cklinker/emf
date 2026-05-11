package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.domain.changed.*} (broadcast) and evicts
 * the matching entry from {@link WorkerCacheManager}'s custom domain cache.
 *
 * <p>The event envelope is a {@code PlatformEvent} whose payload carries the
 * affected domain name (e.g. {@code app.acme.com}). When the domain string is
 * present we evict that specific entry; otherwise we evict the entire cache as
 * a safety fallback.
 */
@Component
public class CustomDomainCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainCacheInvalidationListener.class);

    private final WorkerCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public CustomDomainCacheInvalidationListener(WorkerCacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    public void handleDomainChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                payload = root;
            }
            String domain = textOrNull(payload.get("domain"));
            if (domain == null) {
                log.warn("Custom domain changed event missing 'domain' field — evicting entire cache");
                cacheManager.evictAllCustomDomains();
                return;
            }
            log.info("Custom domain {} changed — evicting local cache entry", domain);
            cacheManager.evictCustomDomain(domain);
        } catch (Exception e) {
            log.error("Failed to process custom domain changed event: {}", e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.stringValue();
        return value == null || value.isBlank() ? null : value;
    }
}
