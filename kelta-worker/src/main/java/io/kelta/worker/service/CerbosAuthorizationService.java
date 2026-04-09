package io.kelta.worker.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResourcesResult;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import io.kelta.worker.config.WorkerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Wraps Cerbos SDK calls with caching, hard timeouts, thread interruption,
 * and a circuit breaker to prevent thread-pool exhaustion.
 *
 * <p>Field access results are cached in-memory (Caffeine) and invalidated
 * via Kafka events when Cerbos policies change. Record access checks are
 * NOT cached because they depend on record attributes (ABAC/CEL rules).
 *
 * <h3>Fail-closed design</h3>
 * <p>When Cerbos is slow or unreachable, all checks return <b>deny</b>.
 * Security first: no request should be allowed without explicit authorization.</p>
 */
@Service
public class CerbosAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationService.class);

    /** Maximum time (seconds) to wait for a single Cerbos gRPC call. */
    private static final long CERBOS_TIMEOUT_SECONDS = 2;

    // Circuit breaker thresholds — configurable via kelta.worker.cerbos-cb-threshold
    // and kelta.worker.cerbos-cb-cooldown-seconds (see WorkerProperties).

    /** Safety-net TTL for cache entries (primary invalidation is event-driven). */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private static final int CACHE_MAX_SIZE = 5_000;

    private final CerbosBlockingClient cerbosClient;
    private final ExecutorService cerbosExecutor;
    private final int circuitBreakerThreshold;
    private final long circuitBreakerCooldownSeconds;

    // Cache for field access: key = "tenantId:profileId:collectionId" → allowed field IDs
    private final Cache<String, List<String>> fieldAccessCache;

    // Metrics
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheEvictions;

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public CerbosAuthorizationService(CerbosBlockingClient cerbosClient, MeterRegistry meterRegistry,
                                       WorkerProperties workerProperties) {
        this.cerbosClient = cerbosClient;
        this.circuitBreakerThreshold = workerProperties.getCerbosCbThreshold();
        this.circuitBreakerCooldownSeconds = workerProperties.getCerbosCbCooldownSeconds();
        this.cerbosExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "cerbos-worker-check");
            t.setDaemon(true);
            return t;
        });

        this.fieldAccessCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .recordStats()
                .build();

        this.cacheHits = Counter.builder("cerbos.cache.hits")
                .tag("service", "worker")
                .register(meterRegistry);
        this.cacheMisses = Counter.builder("cerbos.cache.misses")
                .tag("service", "worker")
                .register(meterRegistry);
        this.cacheEvictions = Counter.builder("cerbos.cache.evictions")
                .tag("service", "worker")
                .register(meterRegistry);

        meterRegistry.gauge("cerbos.cache.size", fieldAccessCache, Cache::estimatedSize);
    }

    public boolean checkRecordAccess(String email, String profileId, String tenantId,
                                      String collectionId, String recordId,
                                      Map<String, Object> recordAttributes, String action) {
        if (isCircuitOpen()) {
            log.warn("Cerbos circuit open — denying record check (fail-closed): user={} record={}", email, recordId);
            return false;
        }

        Future<Boolean> future = cerbosExecutor.submit(() -> {
            Principal principal = buildPrincipal(email, profileId, tenantId);

            Resource resource = Resource.newInstance("record", recordId)
                    .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                    .withAttribute("tenantId", stringAttr(tenantId));

            if (recordAttributes != null) {
                for (Map.Entry<String, Object> entry : recordAttributes.entrySet()) {
                    if (entry.getValue() != null) {
                        resource = resource.withAttribute(entry.getKey(), toAttributeValue(entry.getValue()));
                    }
                }
            }

            resource = resource.withScope(tenantId);

            CheckResult result = cerbosClient.check(principal, resource, action);
            return result.isAllowed(action);
        });

        try {
            boolean allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordSuccess();
            log.debug("Cerbos record check: user={} collection={} record={} action={} allowed={}",
                    email, collectionId, recordId, action, allowed);
            return allowed;
        } catch (TimeoutException e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos record check timed out (fail-closed): user={} record={}", email, recordId);
            return false;
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos record check failed (fail-closed): user={} record={} error={}", email, recordId, e.getMessage());
            return false;
        }
    }

    public boolean checkFieldAccess(String email, String profileId, String tenantId,
                                     String collectionId, String fieldId, String action) {
        if (isCircuitOpen()) {
            log.warn("Cerbos circuit open — denying field check (fail-closed): user={} field={}", email, fieldId);
            return false;
        }

        Future<Boolean> future = cerbosExecutor.submit(() -> {
            Principal principal = buildPrincipal(email, profileId, tenantId);

            Resource resource = Resource.newInstance("field", fieldId)
                    .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                    .withAttribute("fieldId", AttributeValue.stringValue(fieldId))
                    .withScope(tenantId);

            CheckResult result = cerbosClient.check(principal, resource, action);
            return result.isAllowed(action);
        });

        try {
            boolean allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordSuccess();
            log.debug("Cerbos field check: user={} collection={} field={} action={} allowed={}",
                    email, collectionId, fieldId, action, allowed);
            return allowed;
        } catch (TimeoutException e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos field check timed out (fail-closed): user={} field={}", email, fieldId);
            return false;
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos field check failed (fail-closed): user={} field={} error={}", email, fieldId, e.getMessage());
            return false;
        }
    }

    /**
     * Batch-checks field access for multiple fields in a single Cerbos gRPC call.
     * Results are cached per (tenant, profile, collection) since field permissions
     * don't depend on record data.
     *
     * @return the list of fieldIds that are ALLOWED
     */
    public List<String> batchCheckFieldAccess(String email, String profileId, String tenantId,
                                               String collectionId, List<String> fieldIds, String action) {
        if (isCircuitOpen()) {
            log.warn("Cerbos circuit open — denying all field access (fail-closed): user={} collection={}", email, collectionId);
            return List.of();
        }
        if (fieldIds.isEmpty()) {
            return fieldIds;
        }

        // Check cache: if we have a cached result for this (tenant, profile, collection),
        // filter the requested fieldIds against it.
        String cacheKey = fieldCacheKey(tenantId, profileId, collectionId);
        List<String> cachedAllowed = fieldAccessCache.getIfPresent(cacheKey);
        if (cachedAllowed != null) {
            cacheHits.increment();
            Set<String> allowedSet = Set.copyOf(cachedAllowed);
            List<String> result = fieldIds.stream()
                    .filter(allowedSet::contains)
                    .toList();
            log.debug("Cerbos batch field check (cached): user={} collection={} fields={} allowed={}",
                    email, collectionId, fieldIds.size(), result.size());
            return result;
        }
        cacheMisses.increment();

        Future<List<String>> future = cerbosExecutor.submit(() -> {
            Principal principal = buildPrincipal(email, profileId, tenantId);

            var batchRequest = cerbosClient.batch(principal);
            for (String fieldId : fieldIds) {
                Resource resource = Resource.newInstance("field", fieldId)
                        .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                        .withAttribute("fieldId", AttributeValue.stringValue(fieldId))
                        .withScope(tenantId);
                batchRequest.addResourceAndActions(resource, action);
            }

            CheckResourcesResult result = batchRequest.check();
            List<String> allowed = new ArrayList<>();
            for (String fieldId : fieldIds) {
                result.find(fieldId).ifPresentOrElse(
                        checkResult -> {
                            if (checkResult.isAllowed(action)) {
                                allowed.add(fieldId);
                            }
                        },
                        () -> log.warn("Cerbos batch field check: field not found in result (fail-closed): field={}", fieldId)
                );
            }
            return allowed;
        });

        try {
            List<String> allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordSuccess();
            // Cache the allowed fields for this (tenant, profile, collection)
            fieldAccessCache.put(cacheKey, List.copyOf(allowed));
            log.debug("Cerbos batch field check: user={} collection={} fields={} allowed={}",
                    email, collectionId, fieldIds.size(), allowed.size());
            return allowed;
        } catch (TimeoutException e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos batch field check timed out (fail-closed): user={} collection={} fields={}",
                    email, collectionId, fieldIds.size());
            return List.of();
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos batch field check failed (fail-closed): user={} collection={} error={}",
                    email, collectionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Batch-checks record access for multiple records in a single Cerbos gRPC call.
     * NOT cached because record checks depend on record attributes (ABAC/CEL rules).
     *
     * @return the set of recordIds that are ALLOWED
     */
    public Set<String> batchCheckRecordAccess(String email, String profileId, String tenantId,
                                               String collectionId, List<Map<String, Object>> records, String action) {
        if (isCircuitOpen()) {
            log.warn("Cerbos circuit open — denying all record access (fail-closed): user={} collection={}", email, collectionId);
            return Set.of();
        }
        if (records.isEmpty()) {
            return Set.of();
        }

        Future<Set<String>> future = cerbosExecutor.submit(() -> {
            Principal principal = buildPrincipal(email, profileId, tenantId);

            var batchRequest = cerbosClient.batch(principal);
            for (Map<String, Object> record : records) {
                String recordId = (String) record.get("id");
                if (recordId == null) continue;

                Resource resource = Resource.newInstance("record", recordId)
                        .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                        .withAttribute("tenantId", stringAttr(tenantId));

                @SuppressWarnings("unchecked")
                Map<String, Object> attrs = (Map<String, Object>) record.get("attributes");
                if (attrs != null) {
                    for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                        if (entry.getValue() != null) {
                            resource = resource.withAttribute(entry.getKey(), toAttributeValue(entry.getValue()));
                        }
                    }
                }
                resource = resource.withScope(tenantId);
                batchRequest.addResourceAndActions(resource, action);
            }

            CheckResourcesResult result = batchRequest.check();
            Set<String> allowed = new java.util.HashSet<>();
            for (Map<String, Object> record : records) {
                String recordId = (String) record.get("id");
                if (recordId == null) {
                    continue;
                }
                result.find(recordId).ifPresentOrElse(
                        checkResult -> {
                            if (checkResult.isAllowed(action)) {
                                allowed.add(recordId);
                            }
                        },
                        () -> log.warn("Cerbos batch record check: record not found in result (fail-closed): record={}", recordId)
                );
            }
            return allowed;
        });

        try {
            Set<String> allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            recordSuccess();
            log.debug("Cerbos batch record check: user={} collection={} records={} allowed={}",
                    email, collectionId, records.size(), allowed.size());
            return allowed;
        } catch (TimeoutException e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos batch record check timed out (fail-closed): user={} collection={} records={}",
                    email, collectionId, records.size());
            return Set.of();
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.error("Cerbos batch record check failed (fail-closed): user={} collection={} error={}",
                    email, collectionId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Evicts all cached field access entries for a tenant.
     * Called when Cerbos policies are re-synced for a tenant.
     */
    public void evictForTenant(String tenantId) {
        long evicted = fieldAccessCache.asMap().keySet().stream()
                .filter(key -> key.startsWith(tenantId + ":"))
                .peek(fieldAccessCache::invalidate)
                .count();
        if (evicted > 0) {
            cacheEvictions.increment(evicted);
            log.info("Evicted {} cached field access entries for tenant {}", evicted, tenantId);
        }
    }

    // ── Cache key builders ──────────────────────────────────────────────

    private static String fieldCacheKey(String tenantId, String profileId, String collectionId) {
        return tenantId + ":" + profileId + ":" + collectionId;
    }

    // ── Circuit breaker helpers ──────────────────────────────────────────

    private boolean isCircuitOpen() {
        long openUntil = circuitOpenUntil.get();
        if (openUntil == 0) return false;
        if (System.currentTimeMillis() < openUntil) return true;
        circuitOpenUntil.set(0);
        consecutiveFailures.set(0);
        log.info("Cerbos circuit breaker closed — resuming checks");
        return false;
    }

    private Principal buildPrincipal(String email, String profileId, String tenantId) {
        return Principal.newInstance(email, "user")
                .withAttribute("profileId", stringAttr(profileId))
                .withAttribute("tenantId", stringAttr(tenantId));
    }

    private void recordSuccess() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(0);
        }
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= circuitBreakerThreshold) {
            long until = System.currentTimeMillis() + (circuitBreakerCooldownSeconds * 1000);
            circuitOpenUntil.set(until);
            log.error("Cerbos circuit breaker OPEN — denying all checks for {}s after {} consecutive failures",
                    circuitBreakerCooldownSeconds, failures);
        }
    }

    private static AttributeValue stringAttr(String value) {
        return AttributeValue.stringValue(value != null ? value : "");
    }

    private static AttributeValue toAttributeValue(Object value) {
        if (value instanceof String s) return AttributeValue.stringValue(s);
        if (value instanceof Number n) return AttributeValue.doubleValue(n.doubleValue());
        if (value instanceof Boolean b) return AttributeValue.boolValue(b);
        return AttributeValue.stringValue(String.valueOf(value));
    }
}
