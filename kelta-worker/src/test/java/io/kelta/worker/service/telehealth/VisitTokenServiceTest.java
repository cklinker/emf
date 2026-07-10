package io.kelta.worker.service.telehealth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VisitTokenService")
class VisitTokenServiceTest {

    private final VisitTokenService service = new VisitTokenService("unit-test-secret");
    private final Instant now = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    @DisplayName("round-trips a claim while unexpired")
    void roundTrip() {
        String token = service.sign("t1", "appt-1", "u1", now.plusSeconds(3600));
        var claim = service.verify(token, now);
        assertThat(claim).isPresent();
        assertThat(claim.get().tenantId()).isEqualTo("t1");
        assertThat(claim.get().appointmentId()).isEqualTo("appt-1");
        assertThat(claim.get().portalUserId()).isEqualTo("u1");
    }

    @Test
    @DisplayName("rejects expiry, tampering, wrong secret, and garbage")
    void rejections() {
        String token = service.sign("t1", "appt-1", "u1", now.minusSeconds(1));
        assertThat(service.verify(token, now)).isEmpty();

        String valid = service.sign("t1", "appt-1", "u1", now.plusSeconds(3600));
        assertThat(service.verify(valid + "x", now)).isEmpty();
        assertThat(service.verify("no-dot-token", now)).isEmpty();
        assertThat(service.verify("", now)).isEmpty();
        assertThat(service.verify(null, now)).isEmpty();

        VisitTokenService other = new VisitTokenService("different-secret");
        assertThat(other.verify(valid, now)).isEmpty();
    }
}
