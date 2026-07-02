package io.kelta.worker.config;

import io.kelta.worker.service.campaign.CampaignRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules the mass-email campaign runner.
 *
 * <p>Polls {@code email_campaign} on a configurable interval (default 15s) and runs any
 * campaign that is QUEUED or a SCHEDULED campaign whose time has arrived, via
 * {@link CampaignRunnerService}.
 *
 * <p>Enabled by default. Disable with {@code kelta.campaigns.runner-enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.campaigns.runner-enabled", havingValue = "true", matchIfMissing = true)
public class CampaignPollerConfig {

    private static final Logger log = LoggerFactory.getLogger(CampaignPollerConfig.class);

    private final CampaignRunnerService runnerService;

    public CampaignPollerConfig(CampaignRunnerService runnerService) {
        this.runnerService = runnerService;
        log.info("Campaign runner enabled — polling email_campaign for queued/scheduled campaigns");
    }

    @Scheduled(fixedDelayString = "${kelta.campaigns.poll-interval-ms:15000}")
    public void pollAndRun() {
        try {
            runnerService.processClaimableCampaigns();
        } catch (Exception e) {
            log.error("Campaign runner poll cycle failed: {}", e.getMessage(), e);
        }
    }
}
