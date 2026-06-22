package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.observability.DbTenantOtlpRegistry;
import io.kelta.worker.observability.TenantOtlpTargetRepository;
import io.kelta.worker.observability.TenantOtlpTargetRepository.StoredTarget;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantOtlpTargetController")
class TenantOtlpTargetControllerTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private TenantOtlpTargetRepository repository;
    @Mock
    private DbTenantOtlpRegistry registry;
    @Mock
    private CerbosPermissionResolver permissionResolver;
    @Mock
    private BootstrapRepository bootstrapRepository;
    @Mock
    private HttpServletRequest request;

    private TenantOtlpTargetController controller;

    @BeforeEach
    void setUp() {
        controller = new TenantOtlpTargetController(repository, registry, permissionResolver, bootstrapRepository);
    }

    private void grantSetup() {
        when(permissionResolver.getProfileId(request)).thenReturn("p1");
        when(bootstrapRepository.findProfileSystemPermissions("p1"))
                .thenReturn(List.of(Map.of("permission_name", "VIEW_SETUP", "granted", true)));
    }

    private <T> T withTenant(java.util.function.Supplier<T> body) {
        return TenantContext.callWithTenant(TENANT, body::get);
    }

    @Test
    @DisplayName("GET returns the stored target")
    void getReturnsTarget() {
        grantSetup();
        when(repository.find(TENANT))
                .thenReturn(Optional.of(new StoredTarget("https://otlp/v1/traces", Map.of("a", "b"), true)));

        var response = withTenant(() -> controller.get(request));

        assertThat(response.getBody()).containsEntry("endpoint", "https://otlp/v1/traces").containsEntry("enabled", true);
    }

    @Test
    @DisplayName("GET returns disabled when no target is configured")
    void getReturnsDisabledWhenAbsent() {
        grantSetup();
        when(repository.find(TENANT)).thenReturn(Optional.empty());
        var response = withTenant(() -> controller.get(request));
        assertThat(response.getBody()).containsEntry("enabled", false);
    }

    @Test
    @DisplayName("rejects callers without VIEW_SETUP")
    void rejectsWithoutPermission() {
        when(permissionResolver.getProfileId(request)).thenReturn("p1");
        when(bootstrapRepository.findProfileSystemPermissions("p1")).thenReturn(List.of());
        lenient().when(repository.find(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withTenant(() -> controller.get(request)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VIEW_SETUP");
        verify(repository, never()).find(eq(TENANT));
    }

    @Test
    @DisplayName("PUT upserts and invalidates the cache")
    void putUpsertsAndInvalidates() {
        grantSetup();
        Map<String, Object> body = Map.of(
                "endpoint", "https://otlp/v1/traces", "enabled", true, "headers", Map.of("authorization", "Bearer x"));

        var response = withTenant(() -> controller.put(body, request));

        verify(repository).upsert(eq(TENANT), eq("https://otlp/v1/traces"), eq(Map.of("authorization", "Bearer x")), eq(true));
        verify(registry).invalidate(TENANT);
        assertThat(response.getBody()).containsEntry("endpoint", "https://otlp/v1/traces");
    }

    @Test
    @DisplayName("PUT without an endpoint is a 400")
    void putRequiresEndpoint() {
        grantSetup();
        assertThatThrownBy(() -> withTenant(() -> controller.put(Map.of("enabled", true), request)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("endpoint");
        verify(repository, never()).upsert(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("DELETE removes the target and invalidates the cache")
    void deleteRemovesTarget() {
        grantSetup();
        withTenant(() -> controller.delete(request));
        verify(repository).delete(TENANT);
        verify(registry).invalidate(TENANT);
    }
}
