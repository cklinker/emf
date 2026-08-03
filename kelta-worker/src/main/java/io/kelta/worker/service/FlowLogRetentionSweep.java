package io.kelta.worker.service;

import io.kelta.worker.repository.FlowLogRetentionRepository;
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
 * Prunes flow and job execution history older than a retention window —
 * <b>DESTRUCTIVE</b>.
 *
 * <p>Nothing else deletes these tables, so on a busy tenant they become the
 * largest thing in the database and eventually the reason queries slow down.
 * This sweep bounds them.
 *
 * <p><b>Safety design.</b> Dry-run is the DEFAULT
 * ({@code kelta.flow.retention.dry-run:true}), matching the retention-purge
 * posture already in the codebase. In dry-run the sweep only LOGS what it WOULD
 * delete — no rows are touched. Only when the property is explicitly
 * {@code false} does it delete anything. The intended arming procedure is:
 * leave dry-run on for a cycle, read the "would delete" counts, then arm per
 * environment.
 *
 * <p>Deleting a {@code flow_execution} cascades to its {@code flow_step_log} and
 * {@code flow_pending_resume} rows. Only terminal executions are eligible, so a
 * parked Wait state is never collected out from under a live flow.
 *
 * <p>Work is bounded two ways per cycle: {@code batch-size} rows per statement
 * and {@code max-batches} statements per table. A first arming against a large
 * backlog therefore drains over several cycles instead of issuing one enormous
 * delete against a shared database.
 *
 * <p><b>Purged executions disappear from the flow-run observability UI.</b> That
 * is the point of a retention window, but it is worth knowing before arming:
 * the default keeps 60 days.
 */
@Service
public class FlowLogRetentionSweep {

    private static final Logger log = LoggerFactory.getLogger(FlowLogRetentionSweep.class);

    private final FlowLogRetentionRepository repository;
    private final boolean enabled;
    private final boolean dryRun;
    private final int maxAgeDays;
    private final int batchSize;
    private final int maxBatches;
    private final Counter purgedCounter;

    public FlowLogRetentionSweep(
            FlowLogRetentionRepository repository,
            MeterRegistry meterRegistry,
            @Value("${kelta.flow.retention.enabled:true}") boolean enabled,
            @Value("${kelta.flow.retention.dry-run:true}") boolean dryRun,
            @Value("${kelta.flow.retention.max-age-days:60}") int maxAgeDays,
            @Value("${kelta.flow.retention.batch-size:1000}") int batchSize,
            @Value("${kelta.flow.retention.max-batches:20}") int maxBatches) {
        this.repository = repository;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.maxAgeDays = maxAgeDays;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
        this.purgedCounter = Counter.builder("kelta_worker_flowlog_purged")
                .description("Flow and job execution log rows deleted by the retention sweep")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${kelta.flow.retention.poll-interval-ms:3600000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays));
        // Each table is swept in its own try/catch: a failure pruning executions
        // must not stop job logs being pruned, or vice versa.
        try {
            purgeExecutions(cutoff);
        } catch (Exception e) {
            log.error("Flow-log retention (executions) failed: {}", e.getMessage(), e);
        }
        try {
            purgeJobLogs(cutoff);
        } catch (Exception e) {
            log.error("Flow-log retention (job logs) failed: {}", e.getMessage(), e);
        }
    }

    /** Package-private so tests can drive one phase without the scheduler. */
    void purgeExecutions(Instant cutoff) {
        long due = repository.countDueExecutions(cutoff);
        if (due == 0) {
            return;
        }
        if (dryRun) {
            log.info("FlowLogRetentionSweep DRY-RUN: WOULD delete {} flow_execution row(s) "
                    + "older than {} (plus their cascaded step logs); set "
                    + "kelta.flow.retention.dry-run=false to arm", due, cutoff);
            return;
        }
        int deleted = drain(cutoff, repository::deleteExecutionBatch);
        purgedCounter.increment(deleted);
        log.info("Flow-log retention: deleted {} of {} due flow_execution row(s) older than {}",
                deleted, due, cutoff);
    }

    void purgeJobLogs(Instant cutoff) {
        long due = repository.countDueJobLogs(cutoff);
        if (due == 0) {
            return;
        }
        if (dryRun) {
            log.info("FlowLogRetentionSweep DRY-RUN: WOULD delete {} job_execution_log row(s) "
                    + "older than {}", due, cutoff);
            return;
        }
        int deleted = drain(cutoff, repository::deleteJobLogBatch);
        purgedCounter.increment(deleted);
        log.info("Flow-log retention: deleted {} of {} due job_execution_log row(s) older than {}",
                deleted, due, cutoff);
    }

    /**
     * Runs delete batches until one comes back short (nothing left to claim) or
     * the per-cycle batch budget is spent. The short-batch exit also covers the
     * multi-pod case, where another pod may have taken the rest.
     */
    private int drain(Instant cutoff, BatchDeleter deleter) {
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = deleter.delete(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                return total;
            }
        }
        log.info("Flow-log retention hit the per-cycle batch cap ({} x {}); "
                + "the remainder will drain on the next cycle", maxBatches, batchSize);
        return total;
    }

    @FunctionalInterface
    private interface BatchDeleter {
        int delete(Instant cutoff, int batchSize);
    }
}
