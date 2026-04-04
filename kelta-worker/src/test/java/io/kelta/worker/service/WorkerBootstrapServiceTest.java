package io.kelta.worker.service;

import io.kelta.worker.config.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerBootstrapService")
class WorkerBootstrapServiceTest {

    @Mock
    private WorkerProperties workerProperties;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CollectionLifecycleManager lifecycleManager;

    private WorkerBootstrapService service;

    @BeforeEach
    void setUp() {
        when(workerProperties.getId()).thenReturn("worker-1");
        service = new WorkerBootstrapService(workerProperties, jdbcTemplate, lifecycleManager);
    }

    @Test
    @DisplayName("initializes all active collections on startup")
    void initializesAllActiveCollections() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", "col-1", "name", "accounts"),
                Map.of("id", "col-2", "name", "contacts")
        ));

        service.onApplicationReady();

        verify(lifecycleManager).initializeCollection("col-1");
        verify(lifecycleManager).initializeCollection("col-2");
    }

    @Test
    @DisplayName("handles empty collection list gracefully")
    void handlesEmptyCollectionList() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        service.onApplicationReady();

        verify(lifecycleManager, never()).initializeCollection(anyString());
    }

    @Test
    @DisplayName("continues initializing other collections when one fails")
    void continuesOnFailure() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", "col-1", "name", "accounts"),
                Map.of("id", "col-2", "name", "contacts")
        ));
        doThrow(new RuntimeException("init failed")).when(lifecycleManager).initializeCollection("col-1");

        service.onApplicationReady();

        verify(lifecycleManager).initializeCollection("col-1");
        verify(lifecycleManager).initializeCollection("col-2");
    }

    @Test
    @DisplayName("skips collections with null id")
    void skipsNullId() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", null);
        row.put("name", "broken");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        service.onApplicationReady();

        verify(lifecycleManager, never()).initializeCollection(anyString());
    }

    @Test
    @DisplayName("handles database query failure gracefully")
    void handlesDatabaseFailure() {
        when(jdbcTemplate.queryForList(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        service.onApplicationReady();

        verify(lifecycleManager, never()).initializeCollection(anyString());
    }
}
