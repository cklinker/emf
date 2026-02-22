package com.emf.controlplane.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration for the Control Plane Service.
 * Configures Redis caching for collections and JWKS with configurable TTL.
 * Falls back to in-memory caching when Redis is unavailable.
 * 
 * Requirements satisfied:
 * - 14.1: Cache collection definitions in Redis with configurable TTL
 * - 14.2: Cache JWKS keys in Redis with configurable TTL
 * - 14.4: Fallback to direct database/fetch when Redis unavailable
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String COLLECTIONS_CACHE = "collections";
    public static final String COLLECTIONS_LIST_CACHE = "collections-list";
    public static final String JWKS_CACHE = "jwks";
    public static final String PERMISSIONS_CACHE = "permissions";
    public static final String GOVERNOR_LIMITS_CACHE = "governor-limits";

    private final ControlPlaneProperties properties;

    public CacheConfig(ControlPlaneProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a Redis cache manager with configured TTL for each cache.
     * This bean is only created when Redis is available and cache type is redis.
     * 
     * @param connectionFactory Redis connection factory
     * @return RedisCacheManager configured with collection and JWKS caches
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = false)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis cache manager");

        // Create ObjectMapper with JSR310 module for Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Default cache configuration with custom ObjectMapper
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();

        // Configure individual caches with specific TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Collections cache configuration
        int collectionsTtl = properties.getCache().getCollections().getTtl();
        cacheConfigurations.put(COLLECTIONS_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(collectionsTtl)));
        log.info("Collections cache configured with TTL: {} seconds", collectionsTtl);

        // Collections list cache configuration (5-minute TTL)
        int collectionsListTtl = properties.getCache().getCollectionsList().getTtl();
        cacheConfigurations.put(COLLECTIONS_LIST_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(collectionsListTtl)));
        log.info("Collections list cache configured with TTL: {} seconds", collectionsListTtl);

        // JWKS cache configuration
        int jwksTtl = properties.getCache().getJwks().getTtl();
        cacheConfigurations.put(JWKS_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(jwksTtl)));
        log.info("JWKS cache configured with TTL: {} seconds", jwksTtl);

        // Permissions cache configuration (5-minute TTL)
        cacheConfigurations.put(PERMISSIONS_CACHE, defaultConfig
                .entryTtl(Duration.ofMinutes(5)));
        log.info("Permissions cache configured with TTL: 5 minutes");

        // Governor limits cache configuration (60-second TTL)
        int governorLimitsTtl = properties.getCache().getGovernorLimits().getTtl();
        cacheConfigurations.put(GOVERNOR_LIMITS_CACHE, defaultConfig
                .entryTtl(Duration.ofSeconds(governorLimitsTtl)));
        log.info("Governor limits cache configured with TTL: {} seconds", governorLimitsTtl);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Creates an in-memory cache manager as a fallback when Redis is not available.
     * This is useful for development and testing without Redis.
     * 
     * @return ConcurrentMapCacheManager for in-memory caching
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "none", matchIfMissing = true)
    public CacheManager inMemoryCacheManager() {
        log.info("Configuring in-memory cache manager (Redis not available)");
        return new ConcurrentMapCacheManager(COLLECTIONS_CACHE, COLLECTIONS_LIST_CACHE, JWKS_CACHE, PERMISSIONS_CACHE, GOVERNOR_LIMITS_CACHE);
    }
}
