package io.kelta.worker.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CerbosPolicySeeder")
class CerbosPolicySeederTest {

    private static final String LOCK_KEY = "cerbos:policy-seed-lock";

    private CerbosPolicySyncService syncService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private MeterRegistry meterRegistry;
    private CerbosPolicySeeder seeder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        syncService = mock(CerbosPolicySyncService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        meterRegistry = new SimpleMeterRegistry();
        seeder = new CerbosPolicySeeder(syncService, redisTemplate, meterRegistry);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Acquires lock, seeds, then releases lock")
        void acquiresAndSeeds() {
            when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                    .thenReturn(true);

            seeder.seedPolicies();

            verify(syncService).seedBasePolicies();
            verify(syncService).syncAllTenants();
            verify(redisTemplate).delete(LOCK_KEY);
            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.contended").count())
                    .isZero();
        }

        @Test
        @DisplayName("Releases lock even when seeding throws")
        void releasesLockOnFailure() {
            when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                    .thenReturn(true);
            org.mockito.Mockito.doThrow(new RuntimeException("Cerbos down"))
                    .when(syncService).syncAllTenants();

            seeder.seedPolicies();

            verify(redisTemplate).delete(LOCK_KEY);
            assertThat(meterRegistry.counter("cerbos.policy.seed.failures", "tenant", "unknown").count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Lock contention")
    class LockContention {

        @Test
        @DisplayName("Skips seeding and records contended counter when lock is held")
        void skipsWhenLockHeld() {
            when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(valueOps.get(LOCK_KEY)).thenReturn("locked");
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(120L);

            seeder.seedPolicies();

            verify(syncService, never()).seedBasePolicies();
            verify(syncService, never()).syncAllTenants();
            verify(redisTemplate, never()).delete(LOCK_KEY);
            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.contended").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("Tolerates Redis errors when fetching diagnostic lock value/ttl")
        void tolerantDiagnosticReads() {
            when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(valueOps.get(LOCK_KEY)).thenThrow(new RuntimeException("Redis blip"));
            when(redisTemplate.getExpire(LOCK_KEY)).thenThrow(new RuntimeException("Redis blip"));

            seeder.seedPolicies();

            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.contended").count())
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Heartbeat renewal")
    class HeartbeatRenewal {

        @Test
        @DisplayName("renewLock extends lock TTL")
        void renewsLockTtl() {
            when(redisTemplate.expire(LOCK_KEY, CerbosPolicySeeder.LOCK_TTL)).thenReturn(true);

            seeder.renewLock();

            verify(redisTemplate).expire(LOCK_KEY, CerbosPolicySeeder.LOCK_TTL);
            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.expired").count())
                    .isZero();
        }

        @Test
        @DisplayName("renewLock increments expired counter when key already gone")
        void detectsExpiredLockMidSeed() {
            when(redisTemplate.expire(LOCK_KEY, CerbosPolicySeeder.LOCK_TTL)).thenReturn(false);

            seeder.renewLock();

            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.expired").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("renewLock swallows Redis errors without throwing")
        void swallowsRedisErrors() {
            when(redisTemplate.expire(LOCK_KEY, CerbosPolicySeeder.LOCK_TTL))
                    .thenThrow(new RuntimeException("Redis down"));

            seeder.renewLock();

            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.expired").count())
                    .isZero();
        }
    }

    @Nested
    @DisplayName("Contended path then recovery (integration scenario)")
    class ContendedPathRecovery {

        @Test
        @DisplayName("Second worker acquires after first worker's lock TTL expires")
        void recoversAfterCrashedSeeder() {
            // First setIfAbsent: worker A's prior crashed run still owns lock -> false
            // Second setIfAbsent (after TTL elapsed): lock gone -> true
            when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                    .thenReturn(false)
                    .thenReturn(true);
            when(valueOps.get(LOCK_KEY)).thenReturn("locked");
            when(redisTemplate.getExpire(LOCK_KEY)).thenReturn(45L);

            // First attempt: lock contended, skip seeding
            seeder.seedPolicies();
            verify(syncService, never()).syncAllTenants();
            assertThat(meterRegistry.counter("cerbos.policy.seed.lock.contended").count())
                    .isEqualTo(1.0);

            // Second attempt (simulating restart after TTL): seeding proceeds
            seeder.seedPolicies();
            verify(syncService).seedBasePolicies();
            verify(syncService).syncAllTenants();
            verify(redisTemplate, atLeastOnce()).delete(LOCK_KEY);
        }
    }
}
