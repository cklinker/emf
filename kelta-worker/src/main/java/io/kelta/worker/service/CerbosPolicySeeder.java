package io.kelta.worker.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Seeds Cerbos policies on application startup.
 *
 * <p>Uses a Redis-based distributed lock so only one worker instance
 * seeds policies at a time, preventing simultaneous policy compilation
 * storms against Cerbos.
 *
 * <p>The lock holder publishes a heartbeat every {@link #HEARTBEAT_INTERVAL}
 * to extend the {@link #LOCK_TTL}, so a slow seed will not expire
 * mid-run while a crashed seeder's lock still falls off within the
 * original TTL window.
 */
@Component
public class CerbosPolicySeeder {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicySeeder.class);
    private static final String LOCK_KEY = "cerbos:policy-seed-lock";
    static final Duration LOCK_TTL = Duration.ofMinutes(5);
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final CerbosPolicySyncService syncService;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter contendedCounter;
    private final Counter expiredCounter;

    public CerbosPolicySeeder(CerbosPolicySyncService syncService,
                               StringRedisTemplate redisTemplate,
                               MeterRegistry meterRegistry) {
        this.syncService = syncService;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.contendedCounter = meterRegistry.counter("cerbos.policy.seed.lock.contended");
        this.expiredCounter = meterRegistry.counter("cerbos.policy.seed.lock.expired");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedPolicies() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "locked", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            contendedCounter.increment();
            String currentValue = safeGetLockValue();
            Long ttlSeconds = safeGetLockTtl();
            log.warn(
                    "Cerbos seed lock already held — skipping. lock_value={} ttl_seconds={}",
                    currentValue, ttlSeconds);
            return;
        }

        ScheduledExecutorService heartbeat = startHeartbeat();
        try {
            log.info("Seeding Cerbos policies for all tenants on startup (lock acquired)");
            // Seed base (ancestor) policies first — Cerbos requires these
            // before scoped per-tenant policies can compile
            syncService.seedBasePolicies();
            syncService.syncAllTenants();
            log.info("Cerbos policy seeding complete");
        } catch (Exception e) {
            log.error("Failed to seed Cerbos policies on startup: {}", e.getMessage(), e);
            meterRegistry.counter("cerbos.policy.seed.failures", "tenant", "unknown").increment();
        } finally {
            stopHeartbeat(heartbeat);
            try {
                redisTemplate.delete(LOCK_KEY);
            } catch (Exception e) {
                log.warn("Failed to release Cerbos seed lock: {}", e.getMessage());
            }
        }
    }

    private ScheduledExecutorService startHeartbeat() {
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cerbos-seed-lock-heartbeat");
            t.setDaemon(true);
            return t;
        });
        long intervalSeconds = HEARTBEAT_INTERVAL.toSeconds();
        heartbeat.scheduleAtFixedRate(this::renewLock, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        return heartbeat;
    }

    private void stopHeartbeat(ScheduledExecutorService heartbeat) {
        heartbeat.shutdownNow();
        try {
            heartbeat.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void renewLock() {
        try {
            Boolean renewed = redisTemplate.expire(LOCK_KEY, LOCK_TTL);
            if (!Boolean.TRUE.equals(renewed)) {
                expiredCounter.increment();
                log.warn("Cerbos seed lock expired mid-seed — key missing during heartbeat renew");
            }
        } catch (Exception e) {
            log.warn("Failed to renew Cerbos seed lock heartbeat: {}", e.getMessage());
        }
    }

    private String safeGetLockValue() {
        try {
            return redisTemplate.opsForValue().get(LOCK_KEY);
        } catch (Exception e) {
            return "<error: " + e.getMessage() + ">";
        }
    }

    private Long safeGetLockTtl() {
        try {
            return redisTemplate.getExpire(LOCK_KEY);
        } catch (Exception e) {
            return null;
        }
    }
}
