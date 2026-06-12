package io.kelta.runtime.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTTP parsing of the {@code IN} / {@code ANY} filter operator
 * on {@link FilterCondition#fromParams(Map)}.
 */
class FilterConditionInOperatorTest {

    @Test
    @DisplayName("filter[id][in]=a,b,c parses as IN with a List of values")
    void in_lowercase_parsesCsvAsList() {
        Map<String, String> params = Map.of("filter[id][in]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        FilterCondition f = filters.get(0);
        assertEquals("id", f.fieldName());
        assertEquals(FilterOperator.IN, f.operator());
        assertInstanceOf(Collection.class, f.value());
        assertEquals(List.of("a", "b", "c"), List.copyOf((Collection<?>) f.value()));
    }

    @Test
    @DisplayName("filter[id][IN]=... is case-insensitive on the operator token")
    void in_uppercase_parsesSame() {
        Map<String, String> params = Map.of("filter[id][IN]", "u1,u2");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of("u1", "u2"), List.copyOf((Collection<?>) filters.get(0).value()));
    }

    @Test
    @DisplayName("ANY is accepted as an alias for IN")
    void any_alias_mapsToIn() {
        Map<String, String> params = Map.of("filter[status][any]", "active,pending");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of("active", "pending"),
                List.copyOf((Collection<?>) filters.get(0).value()));
    }

    @Test
    @DisplayName("Single value (no comma) still produces a one-element list")
    void in_singleValue_producesOneElementList() {
        Map<String, String> params = Map.of("filter[id][in]", "only");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(List.of("only"), List.copyOf((Collection<?>) filters.get(0).value()));
    }

    @Test
    @DisplayName("Whitespace and empty segments are trimmed and dropped")
    void in_trimsWhitespaceAndDropsEmpty() {
        Map<String, String> params = Map.of("filter[id][in]", " a , ,b ,, c ");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(List.of("a", "b", "c"),
                List.copyOf((Collection<?>) filters.get(0).value()));
    }

    @Test
    @DisplayName("Blank IN value yields an empty list (matches no rows in SQL)")
    void in_blankValue_yieldsEmptyList() {
        Map<String, String> params = Map.of("filter[id][in]", "");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertTrue(((Collection<?>) filters.get(0).value()).isEmpty());
    }

    @Test
    @DisplayName("List at the cap (200 values) is accepted")
    void in_atCap_isAccepted() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][in]", csv);

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(FilterCondition.MAX_IN_LIST_SIZE,
                ((Collection<?>) filters.get(0).value()).size());
    }

    @Test
    @DisplayName("List over the cap raises InvalidQueryException (mapped to 400)")
    void in_overCap_throwsInvalidQueryException() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][in]", csv);

        InvalidQueryException ex = assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
        assertEquals("id", ex.getFieldName());
        assertTrue(ex.getReason().contains(String.valueOf(FilterCondition.MAX_IN_LIST_SIZE)),
                "Reason should mention the cap: " + ex.getReason());
    }

    @Test
    @DisplayName("ANY alias also enforces the cap")
    void any_overCap_throwsInvalidQueryException() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][any]", csv);

        assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
    }

    @Test
    @DisplayName("EQ with a comma-separated value remains a single literal (multi-value requires IN)")
    void eq_csvIsTreatedAsSingleLiteral() {
        // Documented footgun: EQ does not split. Lock the behaviour in a test so
        // future code-changes can't silently introduce auto-splitting that would
        // diverge from how the SQL layer interprets the value.
        Map<String, String> params = Map.of("filter[id][eq]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.EQ, filters.get(0).operator());
        assertEquals("a,b,c", filters.get(0).value());
    }

    @Test
    @DisplayName("FilterCondition.in() factory wraps the collection as IN value")
    void inFactory_createsInCondition() {
        FilterCondition f = FilterCondition.in("id", List.of("a", "b"));
        assertEquals("id", f.fieldName());
        assertEquals(FilterOperator.IN, f.operator());
        assertEquals(List.of("a", "b"), List.copyOf((Collection<?>) f.value()));
    }

    @Test
    @DisplayName("IN mixes correctly with other filters in the same request")
    void in_mixesWithOtherOperators() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", "a,b");
        params.put("filter[status][eq]", "active");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(2, filters.size());
        FilterCondition inCond = filters.stream()
                .filter(f -> f.operator() == FilterOperator.IN)
                .findFirst().orElseThrow();
        FilterCondition eqCond = filters.stream()
                .filter(f -> f.operator() == FilterOperator.EQ)
                .findFirst().orElseThrow();
        assertEquals(List.of("a", "b"), List.copyOf((Collection<?>) inCond.value()));
        assertEquals("active", eqCond.value());
    }
}
