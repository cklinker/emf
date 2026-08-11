package io.kelta.worker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CampaignProperties")
class CampaignPropertiesTest {

    private static CampaignProperties with(String trackingSecret) {
        return new CampaignProperties(true, 15_000, 5, 20, 50_000,
                "http://localhost:8080", trackingSecret);
    }

    @Test
    @DisplayName("refuses a blank tracking secret rather than defaulting to a shared value")
    void failsFastWhenTrackingSecretMissing() {
        // This key signs open-pixel, click-redirect and unsubscribe tokens. The previous
        // hardcoded fallback was published in this repository, so production ran on a value
        // anyone could read, announced only by a startup warning.
        for (String missing : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> with(missing))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CAMPAIGN_TRACKING_SECRET");
        }
    }

    @Test
    @DisplayName("accepts a configured secret")
    void acceptsConfiguredSecret() {
        assertThatCode(() -> with("a-real-secret")).doesNotThrowAnyException();
        assertThat(with("a-real-secret").trackingSecret()).isEqualTo("a-real-secret");
    }

    @Test
    @DisplayName("still defaults the non-security fields")
    void defaultsNonSecurityFields() {
        // Only the signing key is fatal — the tuning knobs keep their forgiving defaults.
        CampaignProperties p = new CampaignProperties(true, 0, 0, 0, 0, null, "secret");

        assertThat(p.pollIntervalMs()).isEqualTo(15_000);
        assertThat(p.batchSize()).isEqualTo(5);
        assertThat(p.sendRatePerSecond()).isEqualTo(20);
        assertThat(p.dailySendLimit()).isEqualTo(50_000);
        assertThat(p.trackingBaseUrl()).isEqualTo("http://localhost:8080");
    }
}
