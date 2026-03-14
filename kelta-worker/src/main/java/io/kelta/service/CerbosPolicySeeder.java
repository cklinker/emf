package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Seeds Cerbos policies on application startup.
 *
 * <p>Uses a Redis-based distributed lock to ensure only one worker instance
 * seeds policies at a time. This prevents multiple workers from overwhelming
 * Cerbos with simultaneous policy compilations on startup.
 */
@Component
public class CerbosPolicySeeder {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicySeeder.class);
    private static final String LOCK_KEY = "cerbos:policy-seed-lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final CerbosPolicySyncService syncService;
    private final StringRedisTemplate redisTemplate;

    public CerbosPolicySeeder(CerbosPolicySyncService syncService,
                               StringRedisTemplate redisTemplate) {
        this.syncService = syncService;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedPolicies() {
        // Acquire a distributed lock so only one worker seeds at a time.
        // If another worker already holds the lock, skip — it will seed for us.
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "locked", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Another worker is already seeding Cerbos policies — skipping");
            return;
        }

        try {
            log.info("Seeding Cerbos policies for all tenants on startup (lock acquired)");
            // Seed base (ancestor) policies first — Cerbos requires these
            // before scoped per-tenant policies can compile
            syncService.seedBasePolicies();
            syncService.syncAllTenants();
            log.info("Cerbos policy seeding complete");
        } catch (Exception e) {
            log.error("Failed to seed Cerbos policies on startup: {}", e.getMessage(), e);
        } finally {
            try {
                redisTemplate.delete(LOCK_KEY);
            } catch (Exception e) {
                log.warn("Failed to release Cerbos seed lock: {}", e.getMessage());
            }
        }
    }
}
