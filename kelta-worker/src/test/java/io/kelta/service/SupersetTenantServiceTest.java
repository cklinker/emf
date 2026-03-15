package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SupersetTenantService")
class SupersetTenantServiceTest {

    private SupersetApiClient apiClient;
    private SupersetTenantService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(SupersetApiClient.class);
        service = new SupersetTenantService(apiClient);
    }

    @Test
    @DisplayName("creates database connection for new tenant")
    void createsDatabaseConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(-1);
        when(apiClient.createDatabaseConnection(anyString(), eq("acme"), anyString())).thenReturn(42);

        service.ensureDatabaseConnection("tenant-uuid", "acme");

        verify(apiClient).createDatabaseConnection("tenant-uuid", "acme", "superset_reader");
    }

    @Test
    @DisplayName("skips creation when connection already exists")
    void skipsExistingConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(42);

        service.ensureDatabaseConnection("tenant-uuid", "acme");

        verify(apiClient, never()).createDatabaseConnection(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("deletes database connection for tenant")
    void deletesDatabaseConnection() {
        when(apiClient.findDatabaseId("acme")).thenReturn(42);

        service.deleteDatabaseConnection("acme");

        verify(apiClient).deleteDatabaseConnection(42);
    }

    @Test
    @DisplayName("handles missing connection on delete gracefully")
    void handlesDeleteMissingGracefully() {
        when(apiClient.findDatabaseId("acme")).thenReturn(-1);

        service.deleteDatabaseConnection("acme");

        verify(apiClient, never()).deleteDatabaseConnection(anyInt());
    }
}
