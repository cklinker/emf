package io.kelta.worker.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PackageRepository")
class PackageRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private PackageRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new PackageRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Should find all history by tenant ID")
    void shouldFindAllByTenantId() {
        when(jdbcTemplate.queryForList(contains("package_history"), eq("t1")))
                .thenReturn(List.of(Map.of("id", "pkg-1", "name", "test")));

        var result = repository.findAllByTenantId("t1");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("test");
    }

    @Test
    @DisplayName("Should save package history and return ID")
    void shouldSave() {
        when(jdbcTemplate.update(contains("INSERT INTO package_history"), any(Object[].class)))
                .thenReturn(1);

        String id = repository.save("t1", "export-1", "1.0.0", "desc", "export", "success", "[]");
        assertThat(id).isNotNull().hasSize(36); // UUID format
        verify(jdbcTemplate).update(contains("INSERT INTO package_history"), any(Object[].class));
    }

    @Test
    @DisplayName("Should update status")
    void shouldUpdateStatus() {
        repository.updateStatus("pkg-1", "failed", "Connection error");
        verify(jdbcTemplate).update(contains("UPDATE package_history"), eq("failed"),
                eq("Connection error"), any(), eq("pkg-1"));
    }

    @Test
    @DisplayName("Should return empty list for empty ID list")
    void shouldReturnEmptyForEmptyIds() {
        var result = repository.findCollectionsByIds("t1", List.of());
        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Should find collections by IDs")
    void shouldFindCollectionsByIds() {
        when(jdbcTemplate.queryForList(contains("collection"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "col-1", "name", "users")));

        var result = repository.findCollectionsByIds("t1", List.of("col-1"));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should find roles by IDs")
    void shouldFindRolesByIds() {
        when(jdbcTemplate.queryForList(contains("role"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "role-1", "name", "admin")));

        var result = repository.findRolesByIds("t1", List.of("role-1"));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should expose JdbcTemplate")
    void shouldExposeJdbcTemplate() {
        assertThat(repository.getJdbcTemplate()).isSameAs(jdbcTemplate);
    }
}
