package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.nimbusds.jose.jwk.JWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for caching JWKS (JSON Web Key Set) from OIDC providers.
 * Provides cached access to JWKS keys for JWT validation with fallback
 * to direct fetch when Redis is unavailable.
 * 
 * Requirements satisfied:
 * - 4.7: Cache JWKS keys from OIDC providers for JWT validation
 * - 12.5: Cache JWKS keys to minimize external requests
 * - 14.2: Cache JWKS keys in Redis with configurable TTL
 * - 14.4: Fallback to direct fetch when Redis unavailable
 */
@Component
public class JwksCache {

    private static final Logger log = LoggerFactory.getLogger(JwksCache.class);

    private final OidcProviderRepository providerRepository;
    private final RestTemplate restTemplate;

    /**
     * In-memory fallback cache for when Redis is unavailable.
     * This provides resilience when the cache infrastructure fails.
     */
    private final Map<String, JwkSetWrapper> fallbackCache = new ConcurrentHashMap<>();

    public JwksCache(OidcProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Retrieves the JWK Set for a given OIDC provider.
     * Results are cached in Redis with the configured TTL.
     * Falls back to direct fetch if caching fails.
     * 
     * @param providerId The OIDC provider ID
     * @return The JWK Set for the provider, or null if not found
     * 
     * Validates: Requirements 4.7, 12.5, 14.2
     */
    @Cacheable(value = CacheConfig.JWKS_CACHE, key = "#providerId", unless = "#result == null")
    @Nullable
    public JwkSetWrapper getJwkSet(String providerId) {
        log.debug("Cache miss for JWKS - fetching for provider: {}", providerId);
        
        try {
            JwkSetWrapper jwkSet = fetchJwkSetInternal(providerId);
            if (jwkSet != null) {
                // Store in fallback cache for resilience
                fallbackCache.put(providerId, jwkSet);
            }
            return jwkSet;
        } catch (Exception e) {
            log.warn("Failed to fetch JWKS for provider {}, checking fallback cache", providerId, e);
            return fallbackCache.get(providerId);
        }
    }

    /**
     * Retrieves the JWK Set with fallback to direct fetch when Redis is unavailable.
     * This method should be used when cache access might fail.
     * 
     * @param providerId The OIDC provider ID
     * @return The JWK Set for the provider, or null if not found
     * 
     * Validates: Requirement 14.4
     */
    @Nullable
    public JwkSetWrapper getJwkSetWithFallback(String providerId) {
        try {
            return getJwkSet(providerId);
        } catch (Exception e) {
            log.warn("Cache access failed for provider {}, falling back to direct fetch", providerId, e);
            return fetchJwkSetDirect(providerId);
        }
    }

    /**
     * Invalidates the cached JWKS for a specific provider.
     * Should be called when the provider's JWKS URI changes or the provider is deleted.
     * 
     * @param providerId The OIDC provider ID to invalidate
     * 
     * Validates: Requirement 14.3
     */
    @CacheEvict(value = CacheConfig.JWKS_CACHE, key = "#providerId")
    public void invalidate(String providerId) {
        log.info("Invalidating JWKS cache for provider: {}", providerId);
        fallbackCache.remove(providerId);
    }

    /**
     * Invalidates all cached JWKS entries.
     * Should be called when a global refresh is needed.
     */
    @CacheEvict(value = CacheConfig.JWKS_CACHE, allEntries = true)
    public void invalidateAll() {
        log.info("Invalidating all JWKS cache entries");
        fallbackCache.clear();
    }

    /**
     * Refreshes the JWKS cache for a specific provider.
     * This method forces a fresh fetch from the provider's JWKS endpoint
     * and updates the cache. Useful for key rotation scenarios.
     * 
     * @param providerId The OIDC provider ID to refresh
     * @return The refreshed JWK Set wrapper, or null if refresh fails
     * 
     * Validates: Requirement 12.5 (JWKS key rotation support)
     */
    @CacheEvict(value = CacheConfig.JWKS_CACHE, key = "#providerId")
    @Nullable
    public JwkSetWrapper refreshJwkSet(String providerId) {
        log.info("Refreshing JWKS cache for provider: {}", providerId);
        fallbackCache.remove(providerId);
        return getJwkSet(providerId);
    }

    /**
     * Refreshes JWKS cache for all active providers.
     * Useful for proactive key rotation handling.
     * 
     * Validates: Requirement 12.5 (JWKS key rotation support)
     */
    public void refreshAllProviders() {
        log.info("Refreshing JWKS cache for all active providers");
        List<OidcProvider> providers = providerRepository.findByActiveTrue();
        for (OidcProvider provider : providers) {
            try {
                refreshJwkSet(provider.getId());
                log.debug("Successfully refreshed JWKS for provider: {}", provider.getName());
            } catch (Exception e) {
                log.warn("Failed to refresh JWKS for provider {}: {}", provider.getName(), e.getMessage());
            }
        }
    }

    /**
     * Fetches the JWK Set directly from the OIDC provider without caching.
     * Used as a fallback when cache is unavailable.
     * 
     * @param providerId The OIDC provider ID
     * @return The JWK Set for the provider, or null if not found
     * 
     * Validates: Requirement 14.4
     */
    @Nullable
    public JwkSetWrapper fetchJwkSetDirect(String providerId) {
        log.debug("Direct fetch of JWKS for provider: {}", providerId);
        return fetchJwkSetInternal(providerId);
    }

    /**
     * Internal method to fetch JWKS from the OIDC provider's JWKS URI.
     * 
     * @param providerId The OIDC provider ID
     * @return The JWK Set wrapper, or null if provider not found or fetch fails
     */
    @Nullable
    private JwkSetWrapper fetchJwkSetInternal(String providerId) {
        // Get the provider from the database
        OidcProvider provider = providerRepository.findByIdAndActiveTrue(providerId).orElse(null);
        if (provider == null) {
            log.warn("OIDC provider not found or inactive: {}", providerId);
            return null;
        }

        return fetchJwkSetFromUri(provider.getJwksUri());
    }

    /**
     * Fetches the JWK Set from a JWKS URI.
     * 
     * @param jwksUri The JWKS endpoint URI
     * @return The JWK Set wrapper, or null if fetch fails
     */
    @Nullable
    public JwkSetWrapper fetchJwkSetFromUri(String jwksUri) {
        log.debug("Fetching JWKS from URI: {}", jwksUri);

        try {
            String jwksJson = restTemplate.getForObject(jwksUri, String.class);
            if (jwksJson == null) {
                log.warn("Empty response from JWKS URI: {}", jwksUri);
                return null;
            }

            JWKSet jwkSet = JWKSet.parse(jwksJson);
            log.debug("Successfully fetched JWKS with {} keys from {}", jwkSet.getKeys().size(), jwksUri);
            return new JwkSetWrapper(jwkSet);
        } catch (RestClientException e) {
            log.error("Failed to fetch JWKS from URI {}: {}", jwksUri, e.getMessage());
            return null;
        } catch (ParseException e) {
            log.error("Failed to parse JWKS from URI {}: {}", jwksUri, e.getMessage());
            return null;
        }
    }

    /**
     * Wrapper class for JWKSet to enable proper serialization for caching.
     * JWKSet itself is not easily serializable, so we wrap it with JSON representation.
     */
    public static class JwkSetWrapper implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private final String jwksJson;
        private transient JWKSet jwkSet;

        public JwkSetWrapper(JWKSet jwkSet) {
            this.jwkSet = jwkSet;
            this.jwksJson = jwkSet.toString();
        }

        public JwkSetWrapper(String jwksJson) {
            this.jwksJson = jwksJson;
            this.jwkSet = null;
        }

        /**
         * Gets the JWKSet, parsing from JSON if necessary.
         * 
         * @return The JWKSet
         */
        public JWKSet getJwkSet() {
            if (jwkSet == null && jwksJson != null) {
                try {
                    jwkSet = JWKSet.parse(jwksJson);
                } catch (ParseException e) {
                    throw new RuntimeException("Failed to parse cached JWKS", e);
                }
            }
            return jwkSet;
        }

        /**
         * Gets the JSON representation of the JWKSet.
         * 
         * @return The JWKS as JSON string
         */
        public String getJwksJson() {
            return jwksJson;
        }
    }
}
