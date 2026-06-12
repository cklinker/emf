package io.kelta.runtime.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering the {@code IN} (alias {@code ANY}) operator parsed from
 * {@code filter[field][in]=a,b,c} URL query parameters via
 * {@link FilterCondition#fromParams(Map)}.
 */
class FilterConditionInOperatorTest {

    @Test
    @DisplayName("filter[id][in]=a,b,c parses to IN with a list of values")
    void parsesInOperatorWithCommaSeparatedValues() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        FilterCondition cond = filters.get(0);
        assertEquals("id", cond.fieldName());
        assertEquals(FilterOperator.IN, cond.operator());
        assertEquals(List.of("a", "b", "c"), cond.value());
    }

    @Test
    @DisplayName("filter[id][any]=a,b,c is an alias for IN")
    void parsesAnyAliasAsInOperator() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][any]", "uuid-1,uuid-2");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of("uuid-1", "uuid-2"), filters.get(0).value());
    }

    @Test
    @DisplayName("IN list values are trimmed and empty entries dropped")
    void trimsAndDropsEmptyEntries() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][IN]", " a , b ,, c ");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(List.of("a", "b", "c"), filters.get(0).value());
    }

    @Test
    @DisplayName("filter[id][in]= (empty value) parses to an empty list")
    void emptyInValueParsesToEmptyList() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", "");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of(), filters.get(0).value());
    }

    @Test
    @DisplayName("Single-value IN list still parses to a one-element list")
    void singleValueInList() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", "only-one");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(List.of("only-one"), filters.get(0).value());
    }

    @Test
    @DisplayName("IN list exactly at MAX_IN_LIST_SIZE is accepted")
    void inListAtCapIsAccepted() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", csv);

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) filters.get(0).value();
        assertEquals(FilterCondition.MAX_IN_LIST_SIZE, values.size());
    }

    @Test
    @DisplayName("Over-cap IN list throws InvalidQueryException (HTTP 400)")
    void overCapInListThrows() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", csv);

        InvalidQueryException ex = assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
        assertEquals("id", ex.getFieldName());
        assertTrue(ex.getReason().contains("IN list"),
                "reason should mention IN list, got: " + ex.getReason());
        assertTrue(ex.getReason().contains(String.valueOf(FilterCondition.MAX_IN_LIST_SIZE)),
                "reason should include the cap, got: " + ex.getReason());
    }

    @Test
    @DisplayName("Over-cap ANY alias list throws InvalidQueryException too")
    void overCapAnyAliasThrows() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][any]", csv);

        assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
    }

    @Test
    @DisplayName("FilterCondition.in factory builds an IN condition with an immutable list")
    void inFactoryBuildsCondition() {
        FilterCondition cond = FilterCondition.in("id", List.of("a", "b"));

        assertEquals("id", cond.fieldName());
        assertEquals(FilterOperator.IN, cond.operator());
        assertEquals(List.of("a", "b"), cond.value());
    }

    @Test
    @DisplayName("EQ with comma-separated value remains a literal string (no auto-split)")
    void eqWithCsvIsLiteral() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][eq]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.EQ, filters.get(0).operator());
        assertEquals("a,b,c", filters.get(0).value(),
                "EQ does not split on commas; multi-value matching requires the IN operator");
    }
}
