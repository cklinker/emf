package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.domain.changed.*} (broadcast) and drops
 * the cached domain → tenant slug mapping so subsequent lookups re-query the
 * database. Every worker pod runs this listener so cache stays consistent
 * across the fleet.
 *
 * <p>The cache is keyed by domain name. If the event payload includes the
 * {@code domain} field, only that entry is evicted; otherwise the full custom
 * domain cache is invalidated as a safety fallback.
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

            JsonNode domainNode = payload.get("domain");
            if (domainNode != null && !domainNode.isNull() && domainNode.isTextual()) {
                String domain = domainNode.stringValue();
                log.info("Custom domain {} changed — evicting worker cache entry", domain);
                cacheManager.evictCustomDomain(domain);
                return;
            }

            log.info("Custom domain changed event missing 'domain' field — evicting all custom domain cache entries");
            cacheManager.evictAllCustomDomains();
        } catch (Exception e) {
            log.error("Failed to process domain changed event: {}", e.getMessage(), e);
        }
    }
}
