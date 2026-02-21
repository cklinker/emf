package com.emf.controlplane.service;

import com.emf.controlplane.repository.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job that purges old security audit log entries based on the
 * configured retention period. Runs daily at 2 AM UTC.
 * Uses batch deletion to avoid long-running transactions.
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.security.audit.retention-days")
public class SecurityAuditPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditPurgeJob.class);

    private final SecurityAuditLogRepository repository;
    private final int retentionDays;
    private final int purgeBatchSize;

    public SecurityAuditPurgeJob(
            SecurityAuditLogRepository repository,
            @Value("${emf.control-plane.security.audit.retention-days}") int retentionDays,
            @Value("${emf.control-plane.security.audit.purge-batch-size:10000}") int purgeBatchSize) {
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.purgeBatchSize = purgeBatchSize;
    }

    /**
     * Purges audit log entries older than the configured retention period.
     * Uses batch deletion to avoid long-running transactions.
     * Runs at 2 AM UTC daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeOldEntries() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Starting audit log purge: retentionDays={}, cutoff={}, batchSize={}",
                retentionDays, cutoff, purgeBatchSize);

        long totalDeleted = 0;
        int batchDeleted;

        do {
            batchDeleted = deleteBatch(cutoff);
            totalDeleted += batchDeleted;
            if (batchDeleted > 0) {
                log.debug("Purged batch of {} audit log entries (total so far: {})",
                        batchDeleted, totalDeleted);
            }
        } while (batchDeleted >= purgeBatchSize);

        if (totalDeleted > 0) {
            log.info("Audit log purge complete: deleted {} entries older than {} days",
                    totalDeleted, retentionDays);
        } else {
            log.info("Audit log purge complete: no entries to purge");
        }
    }

    @Transactional
    public int deleteBatch(Instant cutoff) {
        try {
            return repository.deleteOldEntriesBatch(cutoff, purgeBatchSize);
        } catch (Exception e) {
            log.error("Error during audit log batch purge: {}", e.getMessage(), e);
            return 0;
        }
    }
}
