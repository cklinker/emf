package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SupersetTenantService")
class SupersetTenantServiceTest {

    private SupersetApiClient apiClient;
    private SupersetDatabaseUserService dbUserService;
    private SupersetTenantService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(SupersetApiClient.class);
        dbUserService = mock(SupersetDatabaseUserService.class);
        service = new SupersetTenantService(apiClient, dbUserService);
    }

    @Test
    @DisplayName("creates per-tenant PG user and database connection for new tenant")
    void createsDatabaseConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(-1);
        when(dbUserService.ensureTenantUser("tenant-uuid", "acme")).thenReturn("generated-password");
        when(apiClient.createDatabaseConnection(anyString(), eq("acme"), anyString(), anyString()))
                .thenReturn(42);

        service.ensureDatabaseConnection("tenant-uuid", "acme");

        verify(dbUserService).ensureTenantUser("tenant-uuid", "acme");
        verify(apiClient).createDatabaseConnection("tenant-uuid", "acme",
                "superset_acme", "generated-password");
    }

    @Test
    @DisplayName("skips creation when connection already exists")
    void skipsExistingConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(42);

        service.ensureDatabaseConnection("tenant-uuid", "acme");

        verify(apiClient, never()).createDatabaseConnection(anyString(), anyString(),
                anyString(), anyString());
        verify(dbUserService, never()).ensureTenantUser(anyString(), anyString());
    }

    @Test
    @DisplayName("deletes database connection and PG user for tenant")
    void deletesDatabaseConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(42);

        service.deleteDatabaseConnection("acme");

        verify(apiClient).deleteDatabaseConnection(42);
        verify(dbUserService).dropTenantUser("acme");
    }

    @Test
    @DisplayName("handles missing connection on delete gracefully")
    void handlesDeleteMissingGracefully() {
        when(apiClient.findDatabaseId("acme")).thenReturn(-1);

        service.deleteDatabaseConnection("acme");

        verify(apiClient, never()).deleteDatabaseConnection(anyInt());
        verify(dbUserService).dropTenantUser("acme");
    }
}
