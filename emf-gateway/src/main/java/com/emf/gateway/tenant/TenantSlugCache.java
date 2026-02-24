package com.emf.gateway.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of tenant slug to tenant ID mappings.
 * <p>
 * Populated on startup and refreshed periodically from the worker's
 * {@code /internal/tenants/slug-map} endpoint. Used by {@link TenantSlugExtractionFilter}
 * to resolve tenant slugs from URL path prefixes without a database round-trip.
 */
@Component
public class TenantSlugCache {

    private static final Logger log = LoggerFactory.getLogger(TenantSlugCache.class);

    private final ConcurrentHashMap<String, String> slugToTenantId = new ConcurrentHashMap<>();
    private final WebClient webClient;

    public TenantSlugCache(
            WebClient.Builder webClientBuilder,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
    }

    /**
     * Resolves a tenant slug to a tenant ID.
     *
     * @param slug the tenant slug from the URL path
     * @return the tenant ID if the slug is known, empty otherwise
     */
    public Optional<String> resolve(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(slugToTenantId.get(slug));
    }

    /**
     * Checks whether the given string is a known tenant slug.
     */
    public boolean isKnownSlug(String slug) {
        return slug != null && slugToTenantId.containsKey(slug);
    }

    /**
     * Returns the number of cached slug mappings.
     */
    public int size() {
        return slugToTenantId.size();
    }

    /**
     * Refreshes the cache from the worker service.
     * Called on startup by {@link com.emf.gateway.config.RouteInitializer}
     * and periodically via {@code @Scheduled}.
     */
    @Scheduled(fixedDelayString = "${emf.gateway.tenant-slug.cache-refresh-ms:60000}")
    public void refresh() {
        try {
            Map<String, String> mapping = webClient.get()
                    .uri("/internal/tenants/slug-map")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                    .block();

            if (mapping != null && !mapping.isEmpty()) {
                slugToTenantId.clear();
                slugToTenantId.putAll(mapping);
                log.info("Refreshed tenant slug cache: {} entries", mapping.size());
            } else {
                log.warn("Tenant slug-map returned empty; keeping existing cache ({} entries)", slugToTenantId.size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh tenant slug cache (will retry): {}", e.getMessage());
        }
    }
}
