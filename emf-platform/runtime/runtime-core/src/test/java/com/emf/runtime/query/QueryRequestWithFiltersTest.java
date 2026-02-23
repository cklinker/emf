package com.emf.runtime.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link QueryRequest#withFilters(List)} method.
 *
 * <p>Verifies that withFilters creates a new QueryRequest with the specified
 * filters while preserving all other settings (pagination, sorting, fields).
 */
class QueryRequestWithFiltersTest {

    @Test
    @DisplayName("Should create a new request with the specified filters")
    void withFilters_createsNewRequestWithFilters() {
        QueryRequest original = QueryRequest.defaults();
        assertTrue(original.filters().isEmpty(), "Default request should have no filters");

        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("status", "active"),
                FilterCondition.gt("price", "10.00")
        );

        QueryRequest modified = original.withFilters(newFilters);

        // Verify the new request has the filters
        assertEquals(2, modified.filters().size());
        assertEquals("status", modified.filters().get(0).fieldName());
        assertEquals(FilterOperator.EQ, modified.filters().get(0).operator());
        assertEquals("active", modified.filters().get(0).value());
        assertEquals("price", modified.filters().get(1).fieldName());
        assertEquals(FilterOperator.GT, modified.filters().get(1).operator());

        // Verify the original request is unchanged (immutability)
        assertTrue(original.filters().isEmpty(),
                "Original request should remain unchanged");
    }

    @Test
    @DisplayName("Should preserve pagination settings")
    void withFilters_preservesPagination() {
        Pagination customPagination = new Pagination(3, 50);
        QueryRequest original = new QueryRequest(
                customPagination,
                List.of(),
                List.of(),
                List.of()
        );

        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("tenantId", "tenant-1")
        );

        QueryRequest modified = original.withFilters(newFilters);

        assertEquals(3, modified.pagination().pageNumber(),
                "Page number should be preserved");
        assertEquals(50, modified.pagination().pageSize(),
                "Page size should be preserved");
    }

    @Test
    @DisplayName("Should preserve sorting settings")
    void withFilters_preservesSorting() {
        List<SortField> sorting = List.of(
                SortField.asc("name"),
                SortField.desc("createdAt")
        );
        QueryRequest original = new QueryRequest(
                Pagination.defaults(),
                sorting,
                List.of(),
                List.of()
        );

        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("active", "true")
        );

        QueryRequest modified = original.withFilters(newFilters);

        assertEquals(2, modified.sorting().size(),
                "Sorting should be preserved");
        assertEquals("name", modified.sorting().get(0).fieldName());
        assertEquals(SortDirection.ASC, modified.sorting().get(0).direction());
        assertEquals("createdAt", modified.sorting().get(1).fieldName());
        assertEquals(SortDirection.DESC, modified.sorting().get(1).direction());
    }

    @Test
    @DisplayName("Should preserve field selection settings")
    void withFilters_preservesFields() {
        List<String> fields = List.of("name", "email", "createdAt");
        QueryRequest original = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                fields,
                List.of()
        );

        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("role", "admin")
        );

        QueryRequest modified = original.withFilters(newFilters);

        assertEquals(3, modified.fields().size(),
                "Field selection should be preserved");
        assertTrue(modified.fields().contains("name"));
        assertTrue(modified.fields().contains("email"));
        assertTrue(modified.fields().contains("createdAt"));
    }

    @Test
    @DisplayName("Should create immutable filters list")
    void withFilters_createsImmutableFiltersList() {
        QueryRequest original = QueryRequest.defaults();

        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("status", "active")
        );

        QueryRequest modified = original.withFilters(newFilters);

        // The returned filters list should be immutable
        assertThrows(UnsupportedOperationException.class,
                () -> modified.filters().add(FilterCondition.eq("extra", "value")),
                "Filters list should be immutable");
    }

    @Test
    @DisplayName("Should preserve all settings when combining pagination, sorting, fields, and filters")
    void withFilters_preservesAllSettingsCombined() {
        Pagination customPagination = new Pagination(2, 25);
        List<SortField> sorting = List.of(SortField.desc("updatedAt"));
        List<String> fields = List.of("id", "name");
        List<FilterCondition> originalFilters = List.of(
                FilterCondition.eq("active", "true")
        );

        QueryRequest original = new QueryRequest(customPagination, sorting, fields, originalFilters);

        // Replace filters with new ones
        List<FilterCondition> newFilters = List.of(
                FilterCondition.eq("tenantId", "tenant-1"),
                FilterCondition.eq("active", "true")
        );

        QueryRequest modified = original.withFilters(newFilters);

        // Verify all settings
        assertEquals(2, modified.pagination().pageNumber());
        assertEquals(25, modified.pagination().pageSize());
        assertEquals(1, modified.sorting().size());
        assertEquals("updatedAt", modified.sorting().get(0).fieldName());
        assertEquals(2, modified.fields().size());
        assertEquals(2, modified.filters().size());
        assertEquals("tenantId", modified.filters().get(0).fieldName());
    }
}
