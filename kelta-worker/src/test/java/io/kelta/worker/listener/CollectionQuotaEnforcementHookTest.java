package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionQuotaEnforcementHook")
class CollectionQuotaEnforcementHookTest {

    @Mock
    private TenantQuotaResolver resolver;

    @Mock
    private GovernorLimitsRepository repository;

    private CollectionQuotaEnforcementHook hook;

    @BeforeEach
    void setUp() {
        hook = new CollectionQuotaEnforcementHook(resolver, repository);
    }

    @Test
    @DisplayName("allows create when active < limit")
    void allowsBelowLimit() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_COLLECTIONS)).thenReturn(200);
        when(repository.countActiveCollections("tenant-1")).thenReturn(199);

        BeforeSaveResult result = hook.beforeCreate(Map.of("name", "new"), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects create when active == limit")
    void rejectsAtLimit() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_COLLECTIONS)).thenReturn(10);
        when(repository.countActiveCollections("tenant-1")).thenReturn(10);

        BeforeSaveResult result = hook.beforeCreate(Map.of("name", "new"), "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).message()).contains("Tenant collection quota exceeded (10/10)");
    }

    @Test
    @DisplayName("rejects when active > limit")
    void rejectsAboveLimit() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_COLLECTIONS)).thenReturn(5);
        when(repository.countActiveCollections("tenant-1")).thenReturn(7);

        BeforeSaveResult result = hook.beforeCreate(Map.of("name", "new"), "tenant-1");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("allows when tenantId is missing")
    void allowsWithoutTenant() {
        BeforeSaveResult result = hook.beforeCreate(Map.of("name", "new"), null);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("runs early (negative order)")
    void runsEarly() {
        assertThat(hook.getOrder()).isNegative();
    }

    @Test
    @DisplayName("targets the collections system collection")
    void targetsCollections() {
        assertThat(hook.getCollectionName()).isEqualTo("collections");
    }
}
