package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TenantSlugResolver")
class TenantSlugResolverTest {

    private JdbcTemplate jdbcTemplate;
    private TenantSlugResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        resolver = new TenantSlugResolver(jdbcTemplate);
    }

    @Test
    @DisplayName("Should query DB and return slug for known tenant")
    void shouldReturnSlugForKnownTenant() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("threadline-clothing");

        Optional<String> slug = resolver.resolveSlug("tenant-1");

        assertThat(slug).contains("threadline-clothing");
    }

    @Test
    @DisplayName("Should cache slug and not re-query DB on second call")
    void shouldCacheSlug() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("threadline-clothing");

        resolver.resolveSlug("tenant-1");
        resolver.resolveSlug("tenant-1");
        resolver.resolveSlug("tenant-1");

        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(String.class), eq("tenant-1"));
    }

    @Test
    @DisplayName("Should re-query after eviction")
    void shouldReQueryAfterEviction() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("threadline-clothing");

        resolver.resolveSlug("tenant-1");
        resolver.evict("tenant-1");
        resolver.resolveSlug("tenant-1");

        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), eq("tenant-1"));
    }

    @Test
    @DisplayName("Should return empty for unknown tenant (EmptyResultDataAccessException)")
    void shouldReturnEmptyForUnknownTenant() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("missing")))
                .thenThrow(new EmptyResultDataAccessException(1));

        Optional<String> slug = resolver.resolveSlug("missing");

        assertThat(slug).isEmpty();
    }

    @Test
    @DisplayName("Should NOT cache negative lookups — re-queries on subsequent calls")
    void shouldNotCacheNegativeLookups() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("missing")))
                .thenThrow(new EmptyResultDataAccessException(1));

        resolver.resolveSlug("missing");
        resolver.resolveSlug("missing");

        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), eq("missing"));
    }

    @Test
    @DisplayName("Should return empty for null or blank tenant ID without hitting DB")
    void shouldReturnEmptyForNullOrBlank() {
        assertThat(resolver.resolveSlug(null)).isEmpty();
        assertThat(resolver.resolveSlug("")).isEmpty();
        assertThat(resolver.resolveSlug("   ")).isEmpty();

        verify(jdbcTemplate, times(0)).queryForObject(anyString(), any(Class.class), anyString());
    }

    @Test
    @DisplayName("Should return empty when DB returns blank slug")
    void shouldReturnEmptyForBlankSlug() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenReturn("   ");

        Optional<String> slug = resolver.resolveSlug("tenant-1");

        assertThat(slug).isEmpty();
    }

    @Test
    @DisplayName("Should swallow unexpected exceptions and return empty")
    void shouldSwallowUnexpectedExceptions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1")))
                .thenThrow(new RuntimeException("connection refused"));

        Optional<String> slug = resolver.resolveSlug("tenant-1");

        assertThat(slug).isEmpty();
    }
}
