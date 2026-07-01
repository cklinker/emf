package io.kelta.worker.listener;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("TenantIpAllowlistConfigEventPublisher")
class TenantIpAllowlistConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private TenantIpAllowlistConfigEventPublisher hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new TenantIpAllowlistConfigEventPublisher(publisher);
    }

    // ── Event publishing ──────────────────────────────────────────────────

    @Test
    @DisplayName("publishes when the enabled flag changes")
    void publishesOnEnabledChange() {
        Map<String, Object> previous = new HashMap<>(Map.of("ipAllowlistEnabled", false));
        Map<String, Object> updated = new HashMap<>(Map.of("ipAllowlistEnabled", true));

        hook.afterUpdate("tenant-1", updated, previous, "tenant-1");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(subject.capture(), any(PlatformEvent.class));
        assertThat(subject.getValue()).isEqualTo("kelta.config.tenant.ip-allowlist.changed.tenant-1");
    }

    @Test
    @DisplayName("publishes when the CIDR list changes")
    void publishesOnCidrChange() {
        Map<String, Object> previous = new HashMap<>(Map.of("ipAllowlistCidrs", List.of("10.0.0.0/8")));
        Map<String, Object> updated =
                new HashMap<>(Map.of("ipAllowlistCidrs", List.of("10.0.0.0/8", "192.168.0.0/16")));

        hook.afterUpdate("tenant-1", updated, previous, "tenant-1");

        verify(publisher).publish(eq("kelta.config.tenant.ip-allowlist.changed.tenant-1"), any());
    }

    @Test
    @DisplayName("skips when only unrelated fields change")
    void skipsWhenUnrelated() {
        Map<String, Object> previous = new HashMap<>(Map.of("name", "Acme"));
        Map<String, Object> updated = new HashMap<>(Map.of("name", "Acme Corp"));

        hook.afterUpdate("tenant-1", updated, previous, "tenant-1");

        verify(publisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("always publishes on delete")
    void publishesOnDelete() {
        hook.afterDelete("tenant-1", "tenant-1");
        verify(publisher).publish(eq("kelta.config.tenant.ip-allowlist.changed.tenant-1"), any());
    }

    // ── CIDR validation (before save) ─────────────────────────────────────

    @Test
    @DisplayName("accepts valid IPv4 and IPv6 CIDR ranges")
    void acceptsValidCidrs() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "ipAllowlistCidrs", List.of("10.0.0.0/8", "192.168.1.0/24", "2001:db8::/32")));

        assertThat(hook.beforeCreate(record, "tenant-1").isSuccess()).isTrue();
        assertThat(hook.beforeUpdate("tenant-1", record, Map.of(), "tenant-1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects a malformed CIDR range")
    void rejectsInvalidCidr() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "ipAllowlistCidrs", List.of("10.0.0.0/8", "not-a-cidr")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).field()).isEqualTo("ipAllowlistCidrs");
    }

    @Test
    @DisplayName("rejects an out-of-range prefix length")
    void rejectsBadPrefix() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "ipAllowlistCidrs", List.of("10.0.0.0/33")));

        assertThat(hook.beforeCreate(record, "tenant-1").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("rejects a bare IP without a prefix")
    void rejectsBareIp() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "ipAllowlistCidrs", List.of("10.0.0.1")));

        assertThat(hook.beforeCreate(record, "tenant-1").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("no-op when the record omits the CIDR field")
    void okWhenFieldAbsent() {
        Map<String, Object> record = new HashMap<>(Map.of("ipAllowlistEnabled", true));
        assertThat(hook.beforeCreate(record, "tenant-1").isSuccess()).isTrue();
    }
}
