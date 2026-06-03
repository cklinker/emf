package io.kelta.runtime.query;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {

    @Test
    void fromParams_emptyMap_returnsDefaults() {
        Pagination p = Pagination.fromParams(Map.of());
        assertEquals(1, p.pageNumber());
        assertEquals(Pagination.DEFAULT_PAGE_SIZE, p.pageSize());
    }

    @Test
    void fromParams_bracketSyntax_isHonored() {
        Pagination p = Pagination.fromParams(Map.of("page[number]", "3", "page[size]", "50"));
        assertEquals(3, p.pageNumber());
        assertEquals(50, p.pageSize());
    }

    @Test
    void fromParams_flatPageSize_isIgnored() {
        // Only bracket syntax is honored; a flat "pageSize" is ignored so that
        // clients always opt into the JSON:API form for pagination.
        Pagination p = Pagination.fromParams(Map.of("pageSize", "75"));
        assertEquals(Pagination.DEFAULT_PAGE_SIZE, p.pageSize());
    }

    @Test
    void fromParams_pageSizeAboveHttpCap_isClampedDownToHttpMax() {
        // page[size]=500 used to silently return 20 — verify it now caps to 200.
        Pagination p = Pagination.fromParams(Map.of("page[size]", "500"));
        assertEquals(Pagination.MAX_HTTP_PAGE_SIZE, p.pageSize());
        assertEquals(200, p.pageSize());
    }

    @Test
    void fromParams_pageSizeAtHttpCap_isAccepted() {
        Pagination p = Pagination.fromParams(Map.of("page[size]", "200"));
        assertEquals(200, p.pageSize());
    }

    @Test
    void fromParams_zeroOrNegativePageSize_isClampedToOne() {
        assertEquals(1, Pagination.fromParams(Map.of("page[size]", "0")).pageSize());
        assertEquals(1, Pagination.fromParams(Map.of("page[size]", "-5")).pageSize());
    }

    @Test
    void fromParams_negativePageNumber_isClampedToOne() {
        Pagination p = Pagination.fromParams(Map.of("page[number]", "-3"));
        assertEquals(1, p.pageNumber());
    }

    @Test
    void fromParams_nonNumericValues_fallBackToDefaults() {
        Map<String, String> params = new HashMap<>();
        params.put("page[number]", "abc");
        params.put("page[size]", "xyz");
        Pagination p = Pagination.fromParams(params);
        assertEquals(1, p.pageNumber());
        assertEquals(Pagination.DEFAULT_PAGE_SIZE, p.pageSize());
    }

    @Test
    void constructor_acceptsInternalPageSizesAboveHttpCap() {
        // Internal callers (report export, include resolution) need pages
        // larger than MAX_HTTP_PAGE_SIZE but bounded by MAX_PAGE_SIZE.
        Pagination p = new Pagination(1, 1000);
        assertEquals(1000, p.pageSize());
    }

    @Test
    void constructor_rejectsPageSizeAboveAbsoluteCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pagination(1, Pagination.MAX_PAGE_SIZE + 1));
    }

    @Test
    void httpCapIsBelowAbsoluteCap() {
        // Invariant the two constants describe two different ceilings.
        assertTrue(Pagination.MAX_HTTP_PAGE_SIZE < Pagination.MAX_PAGE_SIZE);
        assertEquals(200, Pagination.MAX_HTTP_PAGE_SIZE);
        assertEquals(1000, Pagination.MAX_PAGE_SIZE);
    }
}
