package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.events.RecordEventPublisher;
import io.kelta.worker.repository.GovernorLimitsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static java.util.Collections.emptyList;

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
    private static final long DEFAULT_AI_TOKENS_PER_MONTH = 1_000_000L;
    private static final boolean DEFAULT_AI_ENABLED = true;

    private static final String DAILY_KEY_PREFIX = "api-calls-daily:";
    private static final String AI_TOKEN_KEY_PREFIX = "ai-tokens-monthly:";

    private final GovernorLimitsRepository repository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RecordEventPublisher recordEventPublisher;

    public GovernorLimitsController(GovernorLimitsRepository repository, ObjectMapper objectMapper,
                                     StringRedisTemplate redisTemplate,
                                     RecordEventPublisher recordEventPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.recordEventPublisher = recordEventPublisher;
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

        // Read AI token usage from Redis (tracked by kelta-ai service)
        long aiTokensUsed = getMonthlyAiTokenCount(tenantId);

        // Build attributes
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("limits", limits);
        attributes.put("apiCallsUsed", apiCallsUsed);
        attributes.put("apiCallsLimit", getIntOrDefault(limits, "apiCallsPerDay", DEFAULT_API_CALLS_PER_DAY));
        attributes.put("usersUsed", usersUsed);
        attributes.put("usersLimit", getIntOrDefault(limits, "maxUsers", DEFAULT_MAX_USERS));
        attributes.put("collectionsUsed", collectionsUsed);
        attributes.put("collectionsLimit", getIntOrDefault(limits, "maxCollections", DEFAULT_MAX_COLLECTIONS));
        attributes.put("aiTokensUsed", aiTokensUsed);
        attributes.put("aiTokensLimit", getLongOrDefault(limits, "aiTokensPerMonth", DEFAULT_AI_TOKENS_PER_MONTH));
        attributes.put("aiEnabled", getBooleanOrDefault(limits, "aiEnabled", DEFAULT_AI_ENABLED));

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

            // Publish a record change event so the gateway refreshes its cache
            publishTenantChangedEvent(tenantId, body);

            // Return the updated status (re-read to confirm)
            return getStatus(tenantId);
        } catch (Exception e) {
            log.error("Failed to update governor-limits for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Failed to update governor limits", e.getMessage()));
        }
    }

    // =========================================================================
    // Event publishing
    // =========================================================================

    /**
     * Publishes a record change event for the tenant so the gateway
     * can refresh its governor limit cache.
     */
    private void publishTenantChangedEvent(String tenantId, Map<String, Object> newLimits) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("limits", newLimits);

            RecordChangedPayload payload = RecordChangedPayload.updated(
                    "tenants", tenantId, data, null, List.of("limits"));

            PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                    "record.updated", tenantId, null, payload);

            recordEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish tenant change event for governor limits update (tenant {}): {}",
                    tenantId, e.getMessage());
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
        } else {
            // Handle PGobject and other types by converting to string first
            String limitsStr = limitsObj.toString();
            if (limitsStr != null && !limitsStr.isBlank()) {
                try {
                    parsed = objectMapper.readValue(limitsStr,
                            objectMapper.getTypeFactory().constructMapType(
                                    HashMap.class, String.class, Object.class));
                } catch (Exception e) {
                    log.warn("Failed to parse limits from {} for tenant {}: {}",
                            limitsObj.getClass().getSimpleName(), tenantId, e.getMessage());
                }
            }
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
        limits.put("aiTokensPerMonth", getLongOrDefault(parsed, "aiTokensPerMonth", DEFAULT_AI_TOKENS_PER_MONTH));
        limits.put("aiEnabled", getBooleanOrDefault(parsed, "aiEnabled", DEFAULT_AI_ENABLED));

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
        limits.put("aiTokensPerMonth", DEFAULT_AI_TOKENS_PER_MONTH);
        limits.put("aiEnabled", DEFAULT_AI_ENABLED);
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

    /**
     * Reads the current month's AI token usage from Redis.
     *
     * <p>The kelta-ai service increments a monthly counter in Redis
     * with key {@code ai-tokens-monthly:<tenantId>:<yyyy-MM>}.
     */
    private long getMonthlyAiTokenCount(String tenantId) {
        try {
            String yearMonth = java.time.YearMonth.now().toString();
            String key = AI_TOKEN_KEY_PREFIX + tenantId + ":" + yearMonth;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("Failed to read monthly AI token count from Redis for tenant {}: {}",
                    tenantId, e.getMessage());
        }
        return 0L;
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    private long getLongOrDefault(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.longValue();
        }
        return defaultValue;
    }

    private boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }
}
