package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.exception.GovernorLimitExceededException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class GovernorLimitsService {

    private static final Logger log = LoggerFactory.getLogger(GovernorLimitsService.class);
    private static final String API_CALL_KEY_PREFIX = "limits:api:";

    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final StringRedisTemplate redisTemplate;

    public GovernorLimitsService(
            TenantService tenantService,
            UserRepository userRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            @Nullable StringRedisTemplate redisTemplate) {
        this.tenantService = tenantService;
        this.userRepository = userRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.redisTemplate = redisTemplate;
    }

    public void checkApiCallLimit(String tenantId) {
        if (redisTemplate == null) {
            log.debug("Redis not available, skipping API call limit check");
            return;
        }

        GovernorLimits limits = tenantService.getGovernorLimits(tenantId);
        String key = apiCallKey(tenantId);
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }

        if (count != null && count > limits.apiCallsPerDay()) {
            throw new GovernorLimitExceededException("API call limit exceeded: " +
                    count + "/" + limits.apiCallsPerDay() + " calls per day");
        }
    }

    public void checkUserLimit(String tenantId) {
        GovernorLimits limits = tenantService.getGovernorLimits(tenantId);
        long currentUsers = userRepository.countByTenantId(tenantId);
        if (currentUsers >= limits.maxUsers()) {
            throw new GovernorLimitExceededException("User limit exceeded: " +
                    currentUsers + "/" + limits.maxUsers() + " users");
        }
    }

    public void checkCollectionLimit(String tenantId) {
        GovernorLimits limits = tenantService.getGovernorLimits(tenantId);
        long currentCollections = collectionRepository.countByTenantIdAndActiveTrue(tenantId);
        if (currentCollections >= limits.maxCollections()) {
            throw new GovernorLimitExceededException("Collection limit exceeded: " +
                    currentCollections + "/" + limits.maxCollections() + " collections");
        }
    }

    public void checkFieldLimit(String tenantId, String collectionId) {
        GovernorLimits limits = tenantService.getGovernorLimits(tenantId);
        long currentFields = fieldRepository.countByCollectionIdAndActiveTrue(collectionId);
        if (currentFields >= limits.maxFieldsPerCollection()) {
            throw new GovernorLimitExceededException("Field limit exceeded: " +
                    currentFields + "/" + limits.maxFieldsPerCollection() + " fields per collection");
        }
    }

    @Cacheable(value = CacheConfig.GOVERNOR_LIMITS_CACHE, key = "#tenantId")
    public GovernorLimitsStatus getStatus(String tenantId) {
        GovernorLimits limits = tenantService.getGovernorLimits(tenantId);
        long users = userRepository.countByTenantId(tenantId);
        long collections = collectionRepository.countByTenantIdAndActiveTrue(tenantId);
        long apiCalls = getApiCallCount(tenantId);

        return new GovernorLimitsStatus(
                limits,
                apiCalls, limits.apiCallsPerDay(),
                users, limits.maxUsers(),
                collections, limits.maxCollections());
    }

    private long getApiCallCount(String tenantId) {
        if (redisTemplate == null) return 0;
        String value = redisTemplate.opsForValue().get(apiCallKey(tenantId));
        return value != null ? Long.parseLong(value) : 0;
    }

    private String apiCallKey(String tenantId) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return API_CALL_KEY_PREFIX + tenantId + ":" + date;
    }

    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS)
    public record GovernorLimitsStatus(
            GovernorLimits limits,
            long apiCallsUsed, int apiCallsLimit,
            long usersUsed, int usersLimit,
            long collectionsUsed, int collectionsLimit
    ) {}
}
