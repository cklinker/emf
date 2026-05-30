package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FieldQuotaEnforcementHook")
class FieldQuotaEnforcementHookTest {

    @Mock
    private TenantQuotaResolver resolver;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FieldQuotaEnforcementHook hook;

    @BeforeEach
    void setUp() {
        hook = new FieldQuotaEnforcementHook(resolver, jdbcTemplate);
    }

    @Test
    @DisplayName("allows when field count < limit")
    void allowsBelowLimit() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_FIELDS_PER_COLLECTION)).thenReturn(500);
        when(jdbcTemplate.queryForObject(
                any(String.class), eq(Integer.class), eq("tenant-1"), eq("col-1")))
                .thenReturn(499);

        BeforeSaveResult result = hook.beforeCreate(
                Map.of("name", "new", "collectionId", "col-1"), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects at limit")
    void rejectsAtLimit() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_FIELDS_PER_COLLECTION)).thenReturn(50);
        when(jdbcTemplate.queryForObject(
                any(String.class), eq(Integer.class), eq("tenant-1"), eq("col-1")))
                .thenReturn(50);

        BeforeSaveResult result = hook.beforeCreate(
                Map.of("name", "new", "collectionId", "col-1"), "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().get(0).message()).contains("Field quota exceeded for this collection (50/50)");
    }

    @Test
    @DisplayName("allows when collectionId missing (lets downstream validation surface)")
    void allowsWithoutCollectionId() {
        BeforeSaveResult result = hook.beforeCreate(Map.of("name", "new"), "tenant-1");
        assertThat(result.isSuccess()).isTrue();
        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Integer.class), any(), any());
    }

    @Test
    @DisplayName("allows without tenant id")
    void allowsWithoutTenant() {
        BeforeSaveResult result = hook.beforeCreate(
                Map.of("name", "new", "collectionId", "col-1"), null);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("allows on DB error (fail-open)")
    void allowsOnDbError() {
        when(resolver.intQuota("tenant-1", TenantTierQuotas.KEY_MAX_FIELDS_PER_COLLECTION)).thenReturn(50);
        when(jdbcTemplate.queryForObject(
                any(String.class), eq(Integer.class), any(Object.class), any(Object.class)))
                .thenThrow(new RuntimeException("db down"));

        BeforeSaveResult result = hook.beforeCreate(
                Map.of("name", "new", "collectionId", "col-1"), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }
}
