package io.kelta.worker.service.campaign;

import io.kelta.worker.config.CampaignProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrackingTokenService")
class TrackingTokenServiceTest {

    private final TrackingTokenService service = new TrackingTokenService(
            new CampaignProperties(true, 15000, 5, 20, 50000, "http://localhost:8080", "unit-test-secret"));

    private final TrackingTokenService otherSecret = new TrackingTokenService(
            new CampaignProperties(true, 15000, 5, 20, 50000, "http://localhost:8080", "different-secret"));

    @Test
    @DisplayName("round-trips a recipient id through sign/verify")
    void roundTrips() {
        String token = service.sign("recipient-123");
        assertThat(service.verify(token)).contains("recipient-123");
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSecret() {
        String token = otherSecret.sign("recipient-123");
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("rejects a tampered payload")
    void rejectsTamperedPayload() {
        String token = service.sign("recipient-123");
        String tampered = "X" + token.substring(1);
        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("rejects tampered signature")
    void rejectsTamperedSignature() {
        String token = service.sign("recipient-123");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("rejects malformed / empty tokens")
    void rejectsMalformed() {
        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("no-dot")).isEmpty();
        assertThat(service.verify(".sig")).isEmpty();
        assertThat(service.verify("payload.")).isEmpty();
        assertThat(service.verify("!!!.!!!")).isEqualTo(Optional.empty());
    }
}
