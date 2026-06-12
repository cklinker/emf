package io.kelta.runtime.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for parsing the {@code IN} / {@code ANY} filter operator from URL query
 * parameters via {@link FilterCondition#fromParams(Map)}.
 */
class FilterConditionInOperatorTest {

    @Test
    @DisplayName("filter[id][in]=a,b,c parses to FilterOperator.IN with list value")
    void parsesInAsList() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][in]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        FilterCondition f = filters.get(0);
        assertEquals("id", f.fieldName());
        assertEquals(FilterOperator.IN, f.operator());
        assertInstanceOf(List.class, f.value());
        assertEquals(List.of("a", "b", "c"), f.value());
    }

    @Test
    @DisplayName("filter[id][any]=… is an alias for IN")
    void parsesAnyAsAliasForIn() {
        Map<String, String> params = new HashMap<>();
        params.put("filter[id][any]", "x,y");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of("x", "y"), filters.get(0).value());
    }

    @Test
    @DisplayName("Uppercase, lowercase and mixed-case operator names all parse")
    void operatorIsCaseInsensitive() {
        Map<String, String> upper = Map.of("filter[id][IN]", "a,b");
        Map<String, String> mixed = Map.of("filter[id][In]", "a,b");
        Map<String, String> alias = Map.of("filter[id][Any]", "a,b");

        assertEquals(FilterOperator.IN, FilterCondition.fromParams(upper).get(0).operator());
        assertEquals(FilterOperator.IN, FilterCondition.fromParams(mixed).get(0).operator());
        assertEquals(FilterOperator.IN, FilterCondition.fromParams(alias).get(0).operator());
    }

    @Test
    @DisplayName("Whitespace around CSV entries is trimmed")
    void trimsWhitespace() {
        Map<String, String> params = Map.of("filter[id][in]", " a , b ,c ");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(List.of("a", "b", "c"), filters.get(0).value());
    }

    @Test
    @DisplayName("Blank CSV entries are dropped")
    void dropsBlankEntries() {
        Map<String, String> params = Map.of("filter[id][in]", "a,,b, ,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(List.of("a", "b", "c"), filters.get(0).value());
    }

    @Test
    @DisplayName("Single-value IN parses to a one-element list")
    void singleValueInIsAList() {
        Map<String, String> params = Map.of("filter[id][in]", "only");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of("only"), filters.get(0).value());
    }

    @Test
    @DisplayName("Empty IN value parses to an empty list")
    void emptyValueIsEmptyList() {
        Map<String, String> params = Map.of("filter[id][in]", "");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.IN, filters.get(0).operator());
        assertEquals(List.of(), filters.get(0).value());
    }

    @Test
    @DisplayName("IN list exactly at the cap parses successfully")
    void atCapParses() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][in]", csv);

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) filters.get(0).value();
        assertEquals(FilterCondition.MAX_IN_LIST_SIZE, values.size());
    }

    @Test
    @DisplayName("IN list over the cap throws InvalidQueryException")
    void overCapThrows() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][in]", csv);

        InvalidQueryException ex = assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
        assertEquals("id", ex.getFieldName());
        assertTrue(ex.getMessage().contains("exceeds maximum"),
                "Expected message to mention cap, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("ANY alias also enforces the cap")
    void anyAliasEnforcesCap() {
        String csv = IntStream.range(0, FilterCondition.MAX_IN_LIST_SIZE + 5)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        Map<String, String> params = Map.of("filter[id][any]", csv);

        assertThrows(InvalidQueryException.class,
                () -> FilterCondition.fromParams(params));
    }

    @Test
    @DisplayName("EQ with comma-separated value remains a single literal")
    void eqWithCommasStaysLiteral() {
        Map<String, String> params = Map.of("filter[name][eq]", "a,b,c");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertEquals(1, filters.size());
        assertEquals(FilterOperator.EQ, filters.get(0).operator());
        assertEquals("a,b,c", filters.get(0).value());
    }

    @Test
    @DisplayName("Unknown operator is silently skipped (existing behavior preserved)")
    void unknownOperatorSkipped() {
        Map<String, String> params = Map.of("filter[id][bogus]", "a,b");

        List<FilterCondition> filters = FilterCondition.fromParams(params);

        assertTrue(filters.isEmpty());
    }
}
