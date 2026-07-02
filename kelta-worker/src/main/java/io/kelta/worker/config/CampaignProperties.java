package io.kelta.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the mass-email campaign subsystem.
 *
 * <p>Bound from the {@code kelta.campaigns.*} block in {@code application.yml}.
 *
 * @param runnerEnabled       whether the scheduled campaign runner polls for work
 * @param pollIntervalMs      poll interval for the campaign runner (ms)
 * @param batchSize           campaigns claimed per poll cycle
 * @param sendRatePerSecond   per-campaign send throttle (recipients/second) — spam-rate control
 * @param dailySendLimit      hard fallback cap on campaign emails/day when no governor limit is set
 * @param trackingBaseUrl     public base URL used to build open/click/unsubscribe links
 *                            (e.g. {@code https://acme.kelta.io}); must be publicly reachable
 * @param trackingSecret      HMAC signing secret for tracking tokens; MUST be overridden in prod
 */
@ConfigurationProperties(prefix = "kelta.campaigns")
public record CampaignProperties(
        boolean runnerEnabled,
        long pollIntervalMs,
        int batchSize,
        int sendRatePerSecond,
        int dailySendLimit,
        String trackingBaseUrl,
        String trackingSecret) {

    public CampaignProperties {
        if (pollIntervalMs <= 0) {
            pollIntervalMs = 15_000;
        }
        if (batchSize <= 0) {
            batchSize = 5;
        }
        if (sendRatePerSecond <= 0) {
            sendRatePerSecond = 20;
        }
        if (dailySendLimit <= 0) {
            dailySendLimit = 50_000;
        }
        if (trackingBaseUrl == null || trackingBaseUrl.isBlank()) {
            trackingBaseUrl = "http://localhost:8080";
        }
        if (trackingSecret == null || trackingSecret.isBlank()) {
            // Dev default — deployments MUST override CAMPAIGN_TRACKING_SECRET.
            trackingSecret = "kelta-dev-campaign-tracking-secret-change-me";
        }
    }
}
