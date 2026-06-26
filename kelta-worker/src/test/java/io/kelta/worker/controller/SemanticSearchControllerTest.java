package io.kelta.worker.controller;

import io.kelta.worker.service.SemanticSearchService;
import io.kelta.worker.service.SemanticSearchService.SemanticSearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchController")
class SemanticSearchControllerTest {

    @Mock
    private SemanticSearchService service;

    private SemanticSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SemanticSearchController(service);
    }

    @Test
    @DisplayName("returns the service result on success")
    void success() {
        when(service.search("docs", "hello", 5)).thenReturn(Map.of("data", List.of()));

        ResponseEntity<Map<String, Object>> response =
                controller.search("docs", Map.of("query", "hello", "limit", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("defaults the limit to 10 when omitted")
    void defaultLimit() {
        when(service.search("docs", "hello", 10)).thenReturn(Map.of("data", List.of()));

        controller.search("docs", Map.of("query", "hello"));

        verify(service).search(eq("docs"), eq("hello"), eq(10));
    }

    @Test
    @DisplayName("maps a SemanticSearchException to 400")
    void invalid() {
        when(service.search("docs", null, 10)).thenThrow(new SemanticSearchException("'query' is required"));

        ResponseEntity<Map<String, Object>> response = controller.search("docs", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("errors");
    }
}
