package io.kelta.worker.controller;

import io.kelta.worker.service.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SearchReindexController Tests")
class SearchReindexControllerTest {

    private SearchIndexService searchIndexService;
    private SearchReindexController controller;

    @BeforeEach
    void setUp() {
        searchIndexService = mock(SearchIndexService.class);
        controller = new SearchReindexController(searchIndexService);
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        void shouldReturnStatsForValidTenant() throws Exception {
            when(searchIndexService.getSearchIndexStats("tenant-1"))
                    .thenReturn(Map.of("totalDocs", 100));

            var response = controller.getStats("tenant-1");
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void shouldReturnBadRequestForNullTenantId() {
            var response = controller.getStats(null);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturnBadRequestForBlankTenantId() {
            var response = controller.getStats("  ");
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void shouldReturn500OnServiceException() throws Exception {
            when(searchIndexService.getSearchIndexStats("tenant-1"))
                    .thenThrow(new RuntimeException("DB error"));

            var response = controller.getStats("tenant-1");
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("triggerReindex")
    class TriggerReindex {

        @Test
        void shouldTriggerAllCollectionsReindex() {
            var response = controller.triggerReindex("tenant-1", "slug-1", null);
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            verify(searchIndexService).rebuildAllCollectionsAsync("tenant-1", "slug-1", null);
        }

        @Test
        void shouldTriggerSpecificCollectionReindex() {
            var body = Map.<String, Object>of("collectionName", "contacts");
            var response = controller.triggerReindex("tenant-1", "slug-1", body);
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            verify(searchIndexService).rebuildAllCollectionsAsync("tenant-1", "slug-1", "contacts");
        }

        @Test
        void shouldReturnBadRequestForMissingTenantId() {
            var response = controller.triggerReindex(null, "slug-1", null);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }
}
