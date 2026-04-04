package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupersetDatasetService")
class SupersetDatasetServiceTest {

    @Mock
    private SupersetApiClient apiClient;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SupersetDatasetService service;

    @BeforeEach
    void setUp() {
        service = new SupersetDatasetService(apiClient, jdbcTemplate);
    }

    @Nested
    @DisplayName("syncDatasets")
    class SyncDatasets {

        @Test
        @DisplayName("creates datasets for collections without existing ones")
        void createsNewDatasets() {
            when(apiClient.findDatabaseId("acme")).thenReturn(1);
            when(apiClient.listDatasets(1)).thenReturn(List.of());
            when(jdbcTemplate.queryForList(contains("collection"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("id", "col-1", "name", "accounts")));
            when(jdbcTemplate.queryForList(contains("field"), eq("col-1")))
                    .thenReturn(List.of(Map.of("name", "email"), Map.of("name", "phone")));
            when(jdbcTemplate.queryForList(contains("profile_field_permission"), eq(String.class), eq("col-1")))
                    .thenReturn(List.of());
            when(apiClient.createDataset(eq(1), eq("kelta_accounts"), anyString())).thenReturn(42);

            service.syncDatasets("tenant-1", "acme");

            verify(apiClient).createDataset(eq(1), eq("kelta_accounts"), contains("SELECT"));
        }

        @Test
        @DisplayName("skips existing datasets")
        void skipsExistingDatasets() {
            when(apiClient.findDatabaseId("acme")).thenReturn(1);
            when(apiClient.listDatasets(1)).thenReturn(List.of(
                    Map.of("table_name", "kelta_accounts")
            ));
            when(jdbcTemplate.queryForList(contains("collection"), eq("tenant-1")))
                    .thenReturn(List.of(Map.of("id", "col-1", "name", "accounts")));

            service.syncDatasets("tenant-1", "acme");

            verify(apiClient, never()).createDataset(anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("skips sync when no database found")
        void skipsWhenNoDatabaseFound() {
            when(apiClient.findDatabaseId("acme")).thenReturn(0);

            service.syncDatasets("tenant-1", "acme");

            verify(apiClient, never()).createDataset(anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("handles API errors gracefully")
        void handlesApiErrors() {
            when(apiClient.findDatabaseId("acme")).thenThrow(new RuntimeException("Superset down"));

            service.syncDatasets("tenant-1", "acme");

            // Should not throw
        }
    }

    @Nested
    @DisplayName("syncDatasetForCollection")
    class SyncDatasetForCollection {

        @Test
        @DisplayName("creates dataset for single collection")
        void createsDatasetForCollection() {
            when(apiClient.findDatabaseId("acme")).thenReturn(1);
            when(apiClient.listDatasets(1)).thenReturn(List.of());
            when(jdbcTemplate.queryForList(contains("field"), eq("col-1")))
                    .thenReturn(List.of(Map.of("name", "name")));
            when(jdbcTemplate.queryForList(contains("profile_field_permission"), eq(String.class), eq("col-1")))
                    .thenReturn(List.of());
            when(apiClient.createDataset(eq(1), eq("kelta_accounts"), anyString())).thenReturn(42);

            service.syncDatasetForCollection("tenant-1", "acme", "col-1", "accounts");

            verify(apiClient).createDataset(eq(1), eq("kelta_accounts"), anyString());
        }

        @Test
        @DisplayName("skips when dataset already exists")
        void skipsExistingDataset() {
            when(apiClient.findDatabaseId("acme")).thenReturn(1);
            when(apiClient.listDatasets(1)).thenReturn(List.of(
                    Map.of("table_name", "kelta_accounts")
            ));

            service.syncDatasetForCollection("tenant-1", "acme", "col-1", "accounts");

            verify(apiClient, never()).createDataset(anyInt(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("listDatasets")
    class ListDatasets {

        @Test
        @DisplayName("returns datasets from API")
        void returnsDatasetsFromApi() {
            when(apiClient.findDatabaseId("acme")).thenReturn(1);
            when(apiClient.listDatasets(1)).thenReturn(List.of(Map.of("id", 1)));

            List<Map<String, Object>> result = service.listDatasets("acme");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list when no database found")
        void returnsEmptyWhenNoDB() {
            when(apiClient.findDatabaseId("acme")).thenReturn(0);

            List<Map<String, Object>> result = service.listDatasets("acme");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list on error")
        void returnsEmptyOnError() {
            when(apiClient.findDatabaseId("acme")).thenThrow(new RuntimeException("Error"));

            List<Map<String, Object>> result = service.listDatasets("acme");

            assertThat(result).isEmpty();
        }
    }
}
