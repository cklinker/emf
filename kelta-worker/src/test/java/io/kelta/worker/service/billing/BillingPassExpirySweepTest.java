package io.kelta.worker.service.billing;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.BillingPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("BillingPassExpirySweep Tests")
class BillingPassExpirySweepTest {

    private BillingPassRepository passRepository;
    private EntitlementService entitlementService;
    private PlatformEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        passRepository = mock(BillingPassRepository.class);
        entitlementService = mock(EntitlementService.class);
        eventPublisher = mock(PlatformEventPublisher.class);
    }

    private BillingPassExpirySweep sweep(boolean enabled) {
        return new BillingPassExpirySweep(passRepository, entitlementService,
                eventPublisher, enabled, 200);
    }

    @Test
    @DisplayName("disabled sweep touches nothing")
    void disabledSweepDoesNothing() {
        sweep(false).sweep();
        verifyNoInteractions(passRepository, entitlementService, eventPublisher);
    }

    @Test
    @DisplayName("nothing due publishes nothing")
    void nothingDuePublishesNothing() {
        when(passRepository.expireDue(anyInt())).thenReturn(List.of());

        sweep(true).sweep();

        verifyNoInteractions(entitlementService, eventPublisher);
    }

    @Test
    @DisplayName("each expired pass invalidates its member and publishes both events")
    void expiredPassInvalidatesAndPublishes() {
        when(passRepository.expireDue(anyInt())).thenReturn(List.of(
                Map.of("tenantId", "t1", "userId", "u1"),
                Map.of("tenantId", "t1", "userId", "u2")));

        sweep(true).sweep();

        verify(entitlementService).invalidate("t1", "u1");
        verify(entitlementService).invalidate("t1", "u2");

        ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(4)).publish(subjects.capture(), any(PlatformEvent.class));
        assertThat(subjects.getAllValues()).contains(
                "kelta.billing.entitlement.changed.t1.u1",
                "kelta.billing.entitlement.changed.t1.u2",
                "kelta.trigger.t1.billing.subscription");
    }

    @Test
    @DisplayName("passes the configured batch size to the repository")
    void passesBatchSize() {
        when(passRepository.expireDue(anyInt())).thenReturn(List.of());

        new BillingPassExpirySweep(passRepository, entitlementService, eventPublisher, true, 50)
                .sweep();

        verify(passRepository).expireDue(50);
    }

    @Test
    @DisplayName("a row missing tenant or member is skipped, not published")
    void skipsIncompleteRows() {
        java.util.Map<String, Object> missingUser = new java.util.HashMap<>();
        missingUser.put("tenantId", "t1");
        missingUser.put("userId", null);

        when(passRepository.expireDue(anyInt())).thenReturn(List.of(missingUser));

        sweep(true).sweep();

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("a repository failure is logged, not propagated to the scheduler")
    void repositoryFailureDoesNotEscape() {
        when(passRepository.expireDue(anyInt())).thenThrow(new IllegalStateException("db down"));

        // A thrown exception would suppress all future runs of a fixedDelay task.
        sweep(true).sweep();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("publishes an EXPIRED payload carrying the member id and tenant")
    void publishesExpiredPayload() {
        when(passRepository.expireDue(anyInt()))
                .thenReturn(List.of(Map.of("tenantId", "t1", "userId", "u1")));

        sweep(true).sweep();

        ArgumentCaptor<PlatformEvent<?>> event = ArgumentCaptor.forClass(PlatformEvent.class);
        // One invalidation event on the member subject; the trigger bridge is a
        // separate publish to a different subject.
        verify(eventPublisher)
                .publish(eq("kelta.billing.entitlement.changed.t1.u1"), event.capture());

        PlatformEvent<?> published = event.getValue();
        assertThat(published.getTenantId()).isEqualTo("t1");
        assertThat(published.getPayload())
                .isInstanceOfSatisfying(
                        io.kelta.runtime.event.BillingEntitlementChangedPayload.class, payload -> {
                            assertThat(payload.getUserId()).isEqualTo("u1");
                            assertThat(payload.getStatus()).isEqualTo("EXPIRED");
                            assertThat(payload.getReason()).isEqualTo("PASS_EXPIRED");
                        });
    }
}
