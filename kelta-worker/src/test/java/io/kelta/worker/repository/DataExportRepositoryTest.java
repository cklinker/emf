package io.kelta.worker.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DataExportRepository")
class DataExportRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private DataExportRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new DataExportRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("create should insert a new export record and return an ID")
    void createShouldInsertAndReturnId() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        String id = repository.create("tenant-1", "My Export", "desc",
                "FULL", null, "CSV", "user@test.com");

        assertThat(id).isNotBlank().hasSize(36);
        verify(jdbcTemplate).update(contains("INSERT INTO data_export"),
                eq(id), eq("tenant-1"), eq("My Export"), eq("desc"),
                eq("FULL"), isNull(), eq("CSV"), eq("user@test.com"));
    }

    @Test
    @DisplayName("findByIdAndTenant should return export when found")
    void findByIdAndTenantShouldReturnWhenFound() {
        Map<String, Object> row = Map.of("id", "exp-1", "status", "COMPLETED");
        when(jdbcTemplate.queryForList(contains("FROM data_export WHERE id"), eq("exp-1"), eq("t1")))
                .thenReturn(List.of(row));

        Optional<Map<String, Object>> result = repository.findByIdAndTenant("exp-1", "t1");

        assertThat(result).isPresent();
        assertThat(result.get().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("findByIdAndTenant should return empty when not found")
    void findByIdAndTenantShouldReturnEmptyWhenNotFound() {
        when(jdbcTemplate.queryForList(contains("FROM data_export WHERE id"), eq("exp-1"), eq("t1")))
                .thenReturn(List.of());

        Optional<Map<String, Object>> result = repository.findByIdAndTenant("exp-1", "t1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTenant should return paginated results")
    void findByTenantShouldReturnPaginatedResults() {
        Map<String, Object> row1 = Map.of("id", "exp-1", "status", "COMPLETED");
        Map<String, Object> row2 = Map.of("id", "exp-2", "status", "PENDING");
        when(jdbcTemplate.queryForList(contains("ORDER BY created_at DESC"), eq("t1"), eq(20), eq(0)))
                .thenReturn(List.of(row1, row2));

        List<Map<String, Object>> results = repository.findByTenant("t1", 20, 0);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("countByTenant should return count")
    void countByTenantShouldReturnCount() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq("t1")))
                .thenReturn(5);

        int count = repository.countByTenant("t1");

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("markInProgress should update status")
    void markInProgressShouldUpdateStatus() {
        when(jdbcTemplate.update(contains("IN_PROGRESS"), any(), any(), eq("exp-1"))).thenReturn(1);

        repository.markInProgress("exp-1");

        verify(jdbcTemplate).update(contains("IN_PROGRESS"), any(), any(), eq("exp-1"));
    }

    @Test
    @DisplayName("markCompleted should update all completion fields")
    void markCompletedShouldUpdateAllFields() {
        when(jdbcTemplate.update(contains("COMPLETED"), any(Object[].class))).thenReturn(1);

        repository.markCompleted("exp-1", 100, 95, "exports/t1/exp-1/data.csv", 12345L);

        verify(jdbcTemplate).update(contains("COMPLETED"),
                eq(100), eq(95), eq("exports/t1/exp-1/data.csv"), eq(12345L),
                any(), any(), eq("exp-1"));
    }

    @Test
    @DisplayName("markFailed should set error message")
    void markFailedShouldSetErrorMessage() {
        when(jdbcTemplate.update(contains("FAILED"), any(Object[].class))).thenReturn(1);

        repository.markFailed("exp-1", "Connection timed out");

        verify(jdbcTemplate).update(contains("FAILED"),
                eq("Connection timed out"), any(), any(), eq("exp-1"));
    }

    @Test
    @DisplayName("cancel should only cancel PENDING exports")
    void cancelShouldOnlyCancelPendingExports() {
        when(jdbcTemplate.update(contains("CANCELLED"), any(), eq("exp-1"), eq("t1")))
                .thenReturn(1);

        int updated = repository.cancel("exp-1", "t1");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate).update(contains("status = 'PENDING'"), any(), eq("exp-1"), eq("t1"));
    }

    @Test
    @DisplayName("findPendingExport should use SKIP LOCKED")
    void findPendingExportShouldUseSkipLocked() {
        Map<String, Object> row = Map.of("id", "exp-1", "status", "PENDING");
        when(jdbcTemplate.queryForList(contains("FOR UPDATE SKIP LOCKED"), eq("exp-1")))
                .thenReturn(List.of(row));

        Optional<Map<String, Object>> result = repository.findPendingExport("exp-1");

        assertThat(result).isPresent();
    }
}
