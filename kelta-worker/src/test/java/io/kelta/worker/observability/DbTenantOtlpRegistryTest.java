package io.kelta.worker.observability;

import io.kelta.worker.observability.TenantOtlpTargetRepository.StoredTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DbTenantOtlpRegistry")
class DbTenantOtlpRegistryTest {

    @Mock
    private TenantOtlpTargetRepository repository;

    @Mock
    private PropertiesTenantOtlpRegistry fallback;

    private DbTenantOtlpRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DbTenantOtlpRegistry(repository, fallback);
    }

    @Test
    @DisplayName("resolves an enabled DB target")
    void resolvesDbTarget() {
        when(repository.find("t1"))
                .thenReturn(Optional.of(new StoredTarget("https://otlp.t1/v1/traces", Map.of("a", "b"), true)));

        Optional<OtlpTarget> target = registry.targetFor("t1");

        assertThat(target).get().extracting(OtlpTarget::endpoint).isEqualTo("https://otlp.t1/v1/traces");
    }

    @Test
    @DisplayName("falls back to properties when the DB target is disabled")
    void fallsBackWhenDisabled() {
        when(repository.find("t1"))
                .thenReturn(Optional.of(new StoredTarget("https://off/v1/traces", Map.of(), false)));
        OtlpTarget propsTarget = new OtlpTarget("https://props/v1/traces", Map.of());
        when(fallback.targetFor("t1")).thenReturn(Optional.of(propsTarget));

        assertThat(registry.targetFor("t1")).contains(propsTarget);
    }

    @Test
    @DisplayName("falls back to properties when there is no DB row")
    void fallsBackWhenNoRow() {
        when(repository.find("t1")).thenReturn(Optional.empty());
        when(fallback.targetFor("t1")).thenReturn(Optional.empty());
        assertThat(registry.targetFor("t1")).isEmpty();
    }

    @Test
    @DisplayName("caches the lookup until invalidated")
    void cachesUntilInvalidated() {
        when(repository.find("t1"))
                .thenReturn(Optional.of(new StoredTarget("https://otlp.t1/v1/traces", Map.of(), true)));

        registry.targetFor("t1");
        registry.targetFor("t1");
        verify(repository, times(1)).find("t1");

        registry.invalidate("t1");
        registry.targetFor("t1");
        verify(repository, times(2)).find("t1");
    }

    @Test
    @DisplayName("returns empty for a blank tenant without touching the DB")
    void emptyForBlankTenant() {
        lenient().when(repository.find(eq("t1"))).thenReturn(Optional.empty());
        assertThat(registry.targetFor("  ")).isEmpty();
        assertThat(registry.targetFor(null)).isEmpty();
    }
}
