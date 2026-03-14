package io.kelta.gateway.authz.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import io.kelta.gateway.auth.GatewayPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
 * <h3>Problem solved</h3>
 * <p>{@code CerbosBlockingClient.check()} is a blocking gRPC call.  When the
 * Cerbos pod restarts or becomes unresponsive, the call blocks for up to the
 * default gRPC deadline (typically 15&nbsp;s).  A simple
 * {@code Mono.timeout()} does <em>not</em> free the underlying
 * {@code boundedElastic} thread &mdash; the gRPC call keeps running, and over
 * many concurrent requests the thread pool is exhausted, causing cascading
 * latency across all gateway routes.</p>
 *
 * <h3>Approach</h3>
 * <ol>
 *   <li>Each Cerbos call runs on a dedicated {@link ExecutorService} via
 *       {@link Future#get(long, TimeUnit)}.  On timeout,
 *       {@link Future#cancel(boolean) cancel(true)} interrupts the thread,
 *       which the gRPC stub honours by throwing a cancelled-status exception
 *       and releasing the thread.</li>
 *   <li>A lightweight circuit breaker tracks consecutive failures.  After
 *       {@value #CIRCUIT_BREAKER_THRESHOLD} failures, Cerbos is bypassed for
 *       {@value #CIRCUIT_BREAKER_COOLDOWN_SECONDS}&nbsp;s, avoiding further
 *       thread consumption while Cerbos recovers.</li>
 *   <li>All failures result in <em>allow</em> (fail-open).  The gateway still
 *       validates JWTs and resolves tenant/profile identity, so this is an
 *       acceptable trade-off.</li>
 * </ol>
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
        // Cached thread pool: threads are created on demand and reused.
        // Idle threads are reclaimed after 60 s.
        this.cerbosExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "cerbos-check");
            t.setDaemon(true);
            return t;
        });
    }

    public Mono<Boolean> checkSystemPermission(GatewayPrincipal principal, String permissionName) {
        if (isCircuitOpen()) {
            log.debug("Cerbos circuit open — skipping system check (fail-open): user={} permission={}",
                    principal.getUsername(), permissionName);
            return Mono.just(true);
        }

        return Mono.fromCallable(() -> {
            Future<Boolean> future = cerbosExecutor.submit(() -> {
                Principal cerbosPrincipal = CerbosPrincipalBuilder.build(principal);
                Resource resource = Resource.newInstance("system_feature", permissionName)
                        .withAttribute("featureName", AttributeValue.stringValue(permissionName))
                        .withScope(principal.getTenantId());

                CheckResult result = cerbosClient.check(cerbosPrincipal, resource, permissionName);
                return result.isAllowed(permissionName);
            });

            try {
                boolean allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                recordSuccess();
                log.debug("Cerbos system check: user={} permission={} allowed={}",
                        principal.getUsername(), permissionName, allowed);
                return allowed;
            } catch (TimeoutException e) {
                future.cancel(true); // interrupts the gRPC call, frees the thread
                recordFailure();
                log.warn("Cerbos system check timed out (fail-open): user={} permission={}",
                        principal.getUsername(), permissionName);
                return true;
            } catch (Exception e) {
                future.cancel(true);
                recordFailure();
                log.warn("Cerbos system check failed (fail-open): user={} permission={} error={}",
                        principal.getUsername(), permissionName, e.getMessage());
                return true;
            }
        })
        .onErrorResume(e -> {
            log.warn("Cerbos system check error (fail-open): user={} permission={} error={}",
                    principal.getUsername(), permissionName, e.getMessage());
            return Mono.just(true);
        });
    }

    public Mono<Boolean> checkObjectPermission(GatewayPrincipal principal,
                                                String collectionId,
                                                String action) {
        if (isCircuitOpen()) {
            log.debug("Cerbos circuit open — skipping object check (fail-open): user={} collection={} action={}",
                    principal.getUsername(), collectionId, action);
            return Mono.just(true);
        }

        return Mono.fromCallable(() -> {
            Future<Boolean> future = cerbosExecutor.submit(() -> {
                Principal cerbosPrincipal = CerbosPrincipalBuilder.build(principal);
                Resource resource = Resource.newInstance("collection", collectionId)
                        .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                        .withScope(principal.getTenantId());

                CheckResult result = cerbosClient.check(cerbosPrincipal, resource, action);
                return result.isAllowed(action);
            });

            try {
                boolean allowed = future.get(CERBOS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                recordSuccess();
                log.debug("Cerbos object check: user={} collection={} action={} allowed={}",
                        principal.getUsername(), collectionId, action, allowed);
                return allowed;
            } catch (TimeoutException e) {
                future.cancel(true);
                recordFailure();
                log.warn("Cerbos object check timed out (fail-open): user={} collection={} action={}",
                        principal.getUsername(), collectionId, action);
                return true;
            } catch (Exception e) {
                future.cancel(true);
                recordFailure();
                log.warn("Cerbos object check failed (fail-open): user={} collection={} action={} error={}",
                        principal.getUsername(), collectionId, action, e.getMessage());
                return true;
            }
        })
        .onErrorResume(e -> {
            log.warn("Cerbos object check error (fail-open): user={} collection={} action={} error={}",
                    principal.getUsername(), collectionId, action, e.getMessage());
            return Mono.just(true);
        });
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
}
