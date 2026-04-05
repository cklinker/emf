package io.kelta.worker.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentRepository")
class EnvironmentRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private EnvironmentRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new EnvironmentRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("create should insert environment and return ID")
    void createShouldInsertAndReturnId() {
        String id = repository.create("t1", "Dev Sandbox", "For development", "SANDBOX",
                null, null, "admin@test.com");

        assertThat(id).isNotNull().hasSize(36);
        verify(jdbcTemplate).update(contains("INSERT INTO environment"), any(Object[].class));
    }

    @Test
    @DisplayName("findByIdAndTenant should return environment when exists")
    void findByIdAndTenantShouldReturnWhenExists() {
        Map<String, Object> envRow = Map.of("id", "env-1", "name", "Dev Sandbox", "type", "SANDBOX");
        when(jdbcTemplate.queryForList(contains("FROM environment WHERE id"), eq("env-1"), eq("t1")))
                .thenReturn(List.of(envRow));

        var result = repository.findByIdAndTenant("env-1", "t1");

        assertThat(result).isPresent();
        assertThat(result.get().get("name")).isEqualTo("Dev Sandbox");
    }

    @Test
    @DisplayName("findByIdAndTenant should return empty when not found")
    void findByIdAndTenantShouldReturnEmptyWhenNotFound() {
        when(jdbcTemplate.queryForList(contains("FROM environment WHERE id"), anyString(), anyString()))
                .thenReturn(List.of());

        var result = repository.findByIdAndTenant("env-nonexistent", "t1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTenant should return all environments for tenant")
    void findByTenantShouldReturnAll() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", "env-1", "name", "Production", "type", "PRODUCTION"),
                Map.of("id", "env-2", "name", "Sandbox", "type", "SANDBOX")
        );
        when(jdbcTemplate.queryForList(contains("FROM environment WHERE tenant_id"), eq("t1")))
                .thenReturn(rows);

        var result = repository.findByTenant("t1");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("updateStatus should update environment status")
    void updateStatusShouldUpdate() {
        repository.updateStatus("env-1", "t1", "ARCHIVED");

        verify(jdbcTemplate).update(contains("UPDATE environment SET status"), eq("ARCHIVED"), eq("env-1"), eq("t1"));
    }

    @Test
    @DisplayName("existsByTenantAndName should return true when exists")
    void existsByTenantAndNameShouldReturnTrue() {
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Integer.class), eq("t1"), eq("Production")))
                .thenReturn(1);

        assertThat(repository.existsByTenantAndName("t1", "Production")).isTrue();
    }

    @Test
    @DisplayName("existsByTenantAndName should return false when not exists")
    void existsByTenantAndNameShouldReturnFalse() {
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Integer.class), eq("t1"), eq("New Env")))
                .thenReturn(0);

        assertThat(repository.existsByTenantAndName("t1", "New Env")).isFalse();
    }

    @Test
    @DisplayName("createSnapshot should insert and return ID")
    void createSnapshotShouldInsertAndReturnId() {
        String id = repository.createSnapshot("t1", "env-1", "Snapshot 1", "{}", 10, "admin@test.com");

        assertThat(id).isNotNull().hasSize(36);
        verify(jdbcTemplate).update(contains("INSERT INTO metadata_snapshot"), any(Object[].class));
    }

    @Test
    @DisplayName("findSnapshotsByEnvironment should return snapshots for environment")
    void findSnapshotsByEnvironmentShouldReturn() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", "snap-1", "name", "Snapshot 1")
        );
        when(jdbcTemplate.queryForList(contains("FROM metadata_snapshot WHERE environment_id"), eq("env-1"), eq("t1")))
                .thenReturn(rows);

        var result = repository.findSnapshotsByEnvironment("env-1", "t1");

        assertThat(result).hasSize(1);
    }
}
