package io.kelta.worker.service;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps Cerbos SDK calls with hard timeouts, thread interruption, and a
 * circuit breaker to prevent thread-pool exhaustion.
 *
 * <h3>Fail-open design</h3>
 * <p>When Cerbos is slow or unreachable, all checks return <b>allow</b>.
 * The gateway already validates JWTs, resolves tenant/profile identity,
 * and performs coarse-grained Cerbos checks — so fail-open at the worker
 * for fine-grained field/record checks is an acceptable trade-off.</p>
 */
@Service
public class CerbosAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationService.class);

    /** Maximum time (seconds) to wait for a single Cerbos gRPC call. */
    private static final long CERBOS_TIMEOUT_SECONDS = 2;

    /** Consecutive failures before the circuit breaker opens. */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;

    /** Seconds to keep the circuit open (skip Cerbos) before retrying. */
    private static final long CIRCUIT_BREAKER_COOLDOWN_SECONDS = 30;

    private final CerbosBlockingClient cerbosClient;
    private final ExecutorService cerbosExecutor;

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public CerbosAuthorizationService(CerbosBlockingClient cerbosClient) {
        this.cerbosClient = cerbosClient;
        this.cerbosExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "cerbos-worker-check");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean checkRecordAccess(String email, String profileId, String tenantId,
                                      String collectionId, String recordId,
                                      Map<String, Object> recordAttributes, String action) {
        if (isCircuitOpen()) {
            log.debug("Cerbos circuit open — skipping record check (fail-open): user={} record={}", email, recordId);
            return true;
        }

        Future<Boolean> future = cerbosExecutor.submit(() -> {
            Principal principal = Principal.newInstance(email, "user")
                    .withAttribute("profileId", stringAttr(profileId))
                    .withAttribute("tenantId", stringAttr(tenantId));

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
            log.warn("Cerbos record check timed out (fail-open): user={} record={}", email, recordId);
            return true;
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.warn("Cerbos record check failed (fail-open): user={} record={} error={}", email, recordId, e.getMessage());
            return true;
        }
    }

    public boolean checkFieldAccess(String email, String profileId, String tenantId,
                                     String collectionId, String fieldId, String action) {
        if (isCircuitOpen()) {
            log.debug("Cerbos circuit open — skipping field check (fail-open): user={} field={}", email, fieldId);
            return true;
        }

        Future<Boolean> future = cerbosExecutor.submit(() -> {
            Principal principal = Principal.newInstance(email, "user")
                    .withAttribute("profileId", stringAttr(profileId))
                    .withAttribute("tenantId", stringAttr(tenantId));

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
            log.warn("Cerbos field check timed out (fail-open): user={} field={}", email, fieldId);
            return true;
        } catch (Exception e) {
            future.cancel(true);
            recordFailure();
            log.warn("Cerbos field check failed (fail-open): user={} field={} error={}", email, fieldId, e.getMessage());
            return true;
        }
    }

    public List<String> batchCheckFieldAccess(String email, String profileId, String tenantId,
                                               String collectionId, List<String> fieldIds, String action) {
        if (isCircuitOpen()) {
            return fieldIds; // fail-open: allow all fields
        }
        return fieldIds.stream()
                .filter(fieldId -> checkFieldAccess(email, profileId, tenantId, collectionId, fieldId, action))
                .toList();
    }

    // ── Circuit breaker helpers ──────────────────────────────────────────

    private boolean isCircuitOpen() {
        long openUntil = circuitOpenUntil.get();
        if (openUntil == 0) return false;
        if (System.currentTimeMillis() < openUntil) return true;
        // Cooldown expired — close the circuit and allow one probe
        circuitOpenUntil.set(0);
        consecutiveFailures.set(0);
        log.info("Cerbos circuit breaker closed — resuming checks");
        return false;
    }

    private void recordSuccess() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(0);
        }
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            long until = System.currentTimeMillis() + (CIRCUIT_BREAKER_COOLDOWN_SECONDS * 1000);
            circuitOpenUntil.set(until);
            log.warn("Cerbos circuit breaker OPEN — skipping checks for {}s after {} consecutive failures",
                    CIRCUIT_BREAKER_COOLDOWN_SECONDS, failures);
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
