package io.kelta.worker.service.analytics;

import io.kelta.worker.repository.AnalyticsEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Prunes {@code analytics_event} rows older than a retention window — <b>DESTRUCTIVE</b>.
 *
 * <p>The capture table (consumer-alerting slice 8) is expected to be the highest-volume system
 * table after {@code record_version}, and nothing else deletes it. This sweep bounds it,
 * applying the flow-log retention lessons from day one — same posture and knobs as
 * {@link io.kelta.worker.service.FlowLogRetentionSweep}.
 *
 * <p><b>Dry-run is the DEFAULT</b> ({@code kelta.analytics.retention.dry-run:true}): the sweep
 * only LOGS what it WOULD delete until an operator explicitly arms it. Runs unscoped on the
 * scheduler thread (no tenant bound → {@code app.current_tenant_id = ''} → {@code admin_bypass}),
 * so one cycle prunes across every tenant. Work is bounded per cycle by {@code batch-size} rows
 * per statement and {@code max-batches} statements, and each batch claims rows with
 * {@code FOR UPDATE SKIP LOCKED} so concurrent pods take disjoint slices.
 */
@Service
public class AnalyticsRetentionSweep {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRetentionSweep.class);

    private final AnalyticsEventRepository repository;
    private final boolean enabled;
    private final boolean dryRun;
    private final int maxAgeDays;
    private final int batchSize;
    private final int maxBatches;
    private final Counter purgedCounter;

    public AnalyticsRetentionSweep(
            AnalyticsEventRepository repository,
            MeterRegistry meterRegistry,
            @Value("${kelta.analytics.retention.enabled:true}") boolean enabled,
            @Value("${kelta.analytics.retention.dry-run:true}") boolean dryRun,
            @Value("${kelta.analytics.retention.max-age-days:90}") int maxAgeDays,
            @Value("${kelta.analytics.retention.batch-size:1000}") int batchSize,
            @Value("${kelta.analytics.retention.max-batches:20}") int maxBatches) {
        this.repository = repository;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.maxAgeDays = maxAgeDays;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
        this.purgedCounter = Counter.builder("kelta_worker_analytics_purged")
                .description("analytics_event rows deleted by the retention sweep")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${kelta.analytics.retention.poll-interval-ms:3600000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays));
        try {
            purge(cutoff);
        } catch (Exception e) {
            log.error("Analytics retention sweep failed: {}", e.getMessage(), e);
        }
    }

    /** Package-private so tests can drive the sweep without the scheduler. */
    void purge(Instant cutoff) {
        long due = repository.countOlderThan(cutoff);
        if (due == 0) {
            return;
        }
        if (dryRun) {
            log.info("AnalyticsRetentionSweep DRY-RUN: WOULD delete {} analytics_event row(s) "
                    + "older than {}; set kelta.analytics.retention.dry-run=false to arm", due, cutoff);
            return;
        }
        int deleted = drain(cutoff);
        purgedCounter.increment(deleted);
        log.info("Analytics retention: deleted {} of {} due analytics_event row(s) older than {}",
                deleted, due, cutoff);
    }

    /**
     * Runs delete batches until one comes back short (nothing left to claim, which also covers
     * another pod having taken the rest) or the per-cycle batch budget is spent.
     */
    private int drain(Instant cutoff) {
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = repository.deleteOlderThan(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
