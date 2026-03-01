package com.emf.worker.controller;

import com.emf.jsonapi.JsonApiResponseBuilder;
import com.emf.worker.repository.GovernorLimitsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * REST controller for the Governor Limits page.
 *
 * <p>Serves {@code GET} and {@code PUT} at {@code /api/governor-limits}.
 * Spring MVC's exact-path matching gives this controller priority over
 * {@code DynamicCollectionRouter}'s {@code {collectionName}} path variable.
 *
 * <p>Returns JSON:API format consistent with other collection endpoints.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/governor-limits")
public class GovernorLimitsController {

    private static final Logger log = LoggerFactory.getLogger(GovernorLimitsController.class);

    // Default limits when tenant has empty {} or missing keys
    private static final int DEFAULT_API_CALLS_PER_DAY = 100_000;
    private static final int DEFAULT_STORAGE_GB = 10;
    private static final int DEFAULT_MAX_USERS = 100;
    private static final int DEFAULT_MAX_COLLECTIONS = 200;
    private static final int DEFAULT_MAX_FIELDS_PER_COLLECTION = 500;
    private static final int DEFAULT_MAX_WORKFLOWS = 50;
    private static final int DEFAULT_MAX_REPORTS = 200;

    private static final String DAILY_KEY_PREFIX = "api-calls-daily:";

    private final GovernorLimitsRepository repository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public GovernorLimitsController(GovernorLimitsRepository repository, ObjectMapper objectMapper,
                                     StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns the governor limits status for the current tenant.
     *
     * <p>Includes the configured limits and current usage metrics
     * (user count, collection count, daily API call count from Redis).
     *
     * @param tenantId the tenant ID from the gateway's X-Tenant-ID header
     * @return governor limits status with limits and usage metrics
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        log.debug("GET governor-limits for tenant {}", tenantId);

        Map<String, Object> limits = loadLimits(tenantId);

        // Count current usage
        int usersUsed = repository.countActiveUsers(tenantId);
        int collectionsUsed = repository.countActiveCollections(tenantId);

        // Read daily API call count from Redis (tracked by gateway's RateLimitFilter)
        int apiCallsUsed = getDailyApiCallCount(tenantId);

        // Build attributes
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("limits", limits);
        attributes.put("apiCallsUsed", apiCallsUsed);
        attributes.put("apiCallsLimit", getIntOrDefault(limits, "apiCallsPerDay", DEFAULT_API_CALLS_PER_DAY));
        attributes.put("usersUsed", usersUsed);
        attributes.put("usersLimit", getIntOrDefault(limits, "maxUsers", DEFAULT_MAX_USERS));
        attributes.put("collectionsUsed", collectionsUsed);
        attributes.put("collectionsLimit", getIntOrDefault(limits, "maxCollections", DEFAULT_MAX_COLLECTIONS));

        log.info("Returning governor-limits for tenant {}: {} users, {} collections",
                tenantId, usersUsed, collectionsUsed);

        return ResponseEntity.ok(JsonApiResponseBuilder.single("governor-limits", tenantId, attributes));
    }

    /**
     * Updates the governor limits for the current tenant.
     *
     * @param tenantId the tenant ID from the gateway's X-Tenant-ID header
     * @param body     the updated limits
     * @return the updated governor limits status
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateLimits(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        log.debug("PUT governor-limits for tenant {}: {}", tenantId, body);

        try {
            String limitsJson = objectMapper.writeValueAsString(body);
            repository.updateTenantLimits(tenantId, limitsJson);

            log.info("Updated governor-limits for tenant {}", tenantId);

            // Return the updated status (re-read to confirm)
            return getStatus(tenantId);
        } catch (Exception e) {
            log.error("Failed to update governor-limits for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Failed to update governor limits", e.getMessage()));
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadLimits(String tenantId) {
        Optional<Object> limitsOpt = repository.findTenantLimits(tenantId);
        if (limitsOpt.isEmpty()) {
            return buildDefaultLimits();
        }

        Object limitsObj = limitsOpt.get();
        Map<String, Object> parsed = null;

        if (limitsObj instanceof String limitsStr && !limitsStr.isBlank()) {
            try {
                parsed = objectMapper.readValue(limitsStr,
                        objectMapper.getTypeFactory().constructMapType(
                                HashMap.class, String.class, Object.class));
            } catch (Exception e) {
                log.warn("Failed to parse limits JSON for tenant {}: {}", tenantId, e.getMessage());
            }
        } else if (limitsObj instanceof Map) {
            parsed = (Map<String, Object>) limitsObj;
        }

        if (parsed == null || parsed.isEmpty()) {
            return buildDefaultLimits();
        }

        // Fill in defaults for any missing keys
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("apiCallsPerDay", getIntOrDefault(parsed, "apiCallsPerDay", DEFAULT_API_CALLS_PER_DAY));
        limits.put("storageGb", getIntOrDefault(parsed, "storageGb", DEFAULT_STORAGE_GB));
        limits.put("maxUsers", getIntOrDefault(parsed, "maxUsers", DEFAULT_MAX_USERS));
        limits.put("maxCollections", getIntOrDefault(parsed, "maxCollections", DEFAULT_MAX_COLLECTIONS));
        limits.put("maxFieldsPerCollection", getIntOrDefault(parsed, "maxFieldsPerCollection", DEFAULT_MAX_FIELDS_PER_COLLECTION));
        limits.put("maxWorkflows", getIntOrDefault(parsed, "maxWorkflows", DEFAULT_MAX_WORKFLOWS));
        limits.put("maxReports", getIntOrDefault(parsed, "maxReports", DEFAULT_MAX_REPORTS));

        return limits;
    }

    private Map<String, Object> buildDefaultLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("apiCallsPerDay", DEFAULT_API_CALLS_PER_DAY);
        limits.put("storageGb", DEFAULT_STORAGE_GB);
        limits.put("maxUsers", DEFAULT_MAX_USERS);
        limits.put("maxCollections", DEFAULT_MAX_COLLECTIONS);
        limits.put("maxFieldsPerCollection", DEFAULT_MAX_FIELDS_PER_COLLECTION);
        limits.put("maxWorkflows", DEFAULT_MAX_WORKFLOWS);
        limits.put("maxReports", DEFAULT_MAX_REPORTS);
        return limits;
    }

    /**
     * Reads today's API call count from Redis.
     *
     * <p>The gateway's {@code RateLimitFilter} increments a daily counter
     * in Redis with key {@code api-calls-daily:<tenantId>:<yyyy-MM-dd>}.
     *
     * @param tenantId the tenant ID
     * @return the number of API calls today, or 0 if unavailable
     */
    private int getDailyApiCallCount(String tenantId) {
        try {
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            String key = DAILY_KEY_PREFIX + tenantId + ":" + today;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("Failed to read daily API call count from Redis for tenant {}: {}",
                    tenantId, e.getMessage());
        }
        return 0;
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }
}
