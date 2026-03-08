package io.kelta.worker.controller;

import io.kelta.worker.service.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SearchController")
class SearchControllerTest {

    private SearchIndexService searchIndexService;
    private SearchController controller;

    @BeforeEach
    void setUp() {
        searchIndexService = mock(SearchIndexService.class);
        controller = new SearchController(searchIndexService);
    }

    @Nested
    @DisplayName("GET /api/_search")
    class Search {

        @Test
        @DisplayName("should return search results")
        @SuppressWarnings("unchecked")
        void shouldReturnResults() {
            when(searchIndexService.search("tenant-1", "widget", 20))
                    .thenReturn(List.of(Map.of(
                            "record_id", "rec-1",
                            "collection_name", "products",
                            "collection_id", "col-1",
                            "display_value", "Widget A",
                            "rank", 0.5f)));

            ResponseEntity<Map<String, Object>> response =
                    controller.search("tenant-1", "widget", 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            assertThat(data).hasSize(1);
            assertThat(data.get(0).get("id")).isEqualTo("rec-1");
            assertThat(data.get(0).get("collectionName")).isEqualTo("products");
            assertThat(data.get(0).get("displayValue")).isEqualTo("Widget A");
        }

        @Test
        @DisplayName("should return empty results for short queries")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyForShortQuery() {
            ResponseEntity<Map<String, Object>> response =
                    controller.search("tenant-1", "ab", 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            assertThat(data).isEmpty();

            verifyNoInteractions(searchIndexService);
        }

        @Test
        @DisplayName("should cap limit at 100")
        void shouldCapLimit() {
            when(searchIndexService.search(anyString(), anyString(), anyInt()))
                    .thenReturn(List.of());

            controller.search("tenant-1", "test query", 500);

            verify(searchIndexService).search("tenant-1", "test query", 100);
        }

        @Test
        @DisplayName("should return 400 for missing tenant ID")
        void shouldReturn400ForMissingTenant() {
            ResponseEntity<Map<String, Object>> response =
                    controller.search("", "test", 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
