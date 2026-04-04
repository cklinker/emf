package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("JdbcUserIdResolver Tests")
class JdbcUserIdResolverTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcUserIdResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        resolver = new JdbcUserIdResolver(jdbcTemplate);
    }

    @Nested
    @DisplayName("Null/Blank Input Handling")
    class NullBlankInput {

        @Test
        void shouldReturnNullWhenIdentifierIsNull() {
            assertNull(resolver.resolve(null, "tenant-1"));
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void shouldReturnBlankWhenIdentifierIsBlank() {
            assertEquals("", resolver.resolve("", "tenant-1"));
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void shouldReturnIdentifierWhenTenantIsNull() {
            assertEquals("user@example.com", resolver.resolve("user@example.com", null));
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void shouldReturnIdentifierWhenTenantIsBlank() {
            assertEquals("user@example.com", resolver.resolve("user@example.com", ""));
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("Database Lookup")
    class DatabaseLookup {

        @Test
        void shouldResolveUserIdFromDatabase() {
            when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1"), eq("user@example.com")))
                    .thenReturn("uuid-123");

            String result = resolver.resolve("user@example.com", "tenant-1");
            assertEquals("uuid-123", result);
        }

        @Test
        void shouldFallbackToEmailOnException() {
            when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
                    .thenThrow(new EmptyResultDataAccessException(1));

            String result = resolver.resolve("user@example.com", "tenant-1");
            assertEquals("user@example.com", result);
        }
    }

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        void shouldCacheResolvedUserId() {
            when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1"), eq("user@example.com")))
                    .thenReturn("uuid-123");

            resolver.resolve("user@example.com", "tenant-1");
            resolver.resolve("user@example.com", "tenant-1");

            verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(String.class), any(), any());
        }

        @Test
        void shouldCachePerTenant() {
            when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-1"), eq("user@example.com")))
                    .thenReturn("uuid-1");
            when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("tenant-2"), eq("user@example.com")))
                    .thenReturn("uuid-2");

            assertEquals("uuid-1", resolver.resolve("user@example.com", "tenant-1"));
            assertEquals("uuid-2", resolver.resolve("user@example.com", "tenant-2"));

            verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(String.class), any(), any());
        }
    }
}
