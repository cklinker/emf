package io.kelta.runtime.cell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcTenantCellResolver")
class JdbcTenantCellResolverTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcTenantCellResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JdbcTenantCellResolver(jdbcTemplate);
    }

    @Test
    @DisplayName("returns DEFAULT_CELL_ID for null or blank tenant id (no DB lookup)")
    void returnsDefaultForBlank() {
        assertThat(resolver.cellFor(null)).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
        assertThat(resolver.cellFor("")).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
        assertThat(resolver.cellFor("   ")).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
        assertThat(resolver.cachedSize()).isZero();
    }

    @Test
    @DisplayName("returns the cell id from DB on first call")
    void returnsCellFromDb() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("cell-paid-1");

        assertThat(resolver.cellFor("tenant-1")).isEqualTo("cell-paid-1");
    }

    @Test
    @DisplayName("caches lookups — second call same tenant hits cache, no DB")
    void cachesLookups() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("cell-1");

        resolver.cellFor("tenant-1");
        resolver.cellFor("tenant-1");
        resolver.cellFor("tenant-1");

        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(String.class), any(Object[].class));
        assertThat(resolver.cachedSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("defaults to DEFAULT_CELL_ID when row missing")
    void defaultsOnMissingRow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("missing")))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(resolver.cellFor("missing")).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
    }

    @Test
    @DisplayName("defaults to DEFAULT_CELL_ID when DB returns null")
    void defaultsOnNullValue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn(null);

        assertThat(resolver.cellFor("tenant-1")).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
    }

    @Test
    @DisplayName("fail-open on DB error — returns default + does not throw")
    void failOpenOnDbError() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenThrow(new RuntimeException("broker down"));

        assertThat(resolver.cellFor("tenant-1")).isEqualTo(TenantCellResolver.DEFAULT_CELL_ID);
    }

    @Test
    @DisplayName("invalidate drops one cache entry; next call re-reads DB")
    void invalidateDropsEntry() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("cell-1");

        resolver.cellFor("tenant-1");
        resolver.invalidate("tenant-1");
        resolver.cellFor("tenant-1");

        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("invalidateAll drops every entry")
    void invalidateAllDropsEverything() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("cell-1");

        resolver.cellFor("a");
        resolver.cellFor("b");
        resolver.cellFor("c");
        assertThat(resolver.cachedSize()).isEqualTo(3);

        resolver.invalidateAll();

        assertThat(resolver.cachedSize()).isZero();
    }
}
