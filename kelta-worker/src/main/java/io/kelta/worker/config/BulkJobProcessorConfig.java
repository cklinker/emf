package io.kelta.worker.config;

import io.kelta.worker.service.BulkJobProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for the bulk job processing subsystem.
 *
 * <p>Polls the bulk_job table on a configurable interval (default 10s)
 * and processes queued jobs via {@link BulkJobProcessorService}.
 *
 * <p>Enabled by default. Disable with {@code kelta.bulk.processor.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.bulk.processor.enabled", havingValue = "true", matchIfMissing = true)
public class BulkJobProcessorConfig {

    private static final Logger log = LoggerFactory.getLogger(BulkJobProcessorConfig.class);

    private final BulkJobProcessorService processorService;

    public BulkJobProcessorConfig(BulkJobProcessorService processorService) {
        this.processorService = processorService;
        log.info("Bulk job processor enabled — polling for queued jobs");
    }

    @Scheduled(fixedDelayString = "${kelta.bulk.processor.poll-interval-ms:10000}")
    public void pollAndProcess() {
        try {
            processorService.processQueuedJobs();
        } catch (Exception e) {
            log.error("Bulk job processor poll cycle failed: {}", e.getMessage(), e);
        }
    }
}
