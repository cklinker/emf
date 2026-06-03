package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that every 4xx response from the atomic-operations endpoint carries a populated
 * JSON:API error object — status, code, title, detail — never an empty {} placeholder.
 */
@DisplayName("AtomicOperationsController error envelope")
class AtomicOperationsControllerTest {

    private AtomicOperationsController controller;

    @BeforeEach
    void setUp() {
        QueryEngine queryEngine = mock(QueryEngine.class);
        CollectionRegistry registry = mock(CollectionRegistry.class);
        controller = new AtomicOperationsController(queryEngine, registry, 100);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("missing atomic:operations key returns 400 with INVALID_PAYLOAD")
    @SuppressWarnings("unchecked")
    void missingOperationsKey() {
        ResponseEntity<?> response = controller.executeOperations(Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> err = ((List<Map<String, Object>>) body.get("errors")).get(0);
        assertThat(err.get("status")).isEqualTo("400");
        assertThat(err.get("code")).isEqualTo("INVALID_PAYLOAD");
        assertThat(err.get("title")).isEqualTo("Invalid request");
        assertThat(err.get("detail")).isEqualTo("Missing 'atomic:operations' array");
    }

    @Test
    @DisplayName("empty operations array returns 400 with EMPTY_BATCH")
    @SuppressWarnings("unchecked")
    void emptyOperationsArray() {
        ResponseEntity<?> response = controller.executeOperations(
                Map.of("atomic:operations", List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> err = ((List<Map<String, Object>>) body.get("errors")).get(0);
        assertThat(err.get("status")).isEqualTo("400");
        assertThat(err.get("code")).isEqualTo("EMPTY_BATCH");
        assertThat(err.get("detail")).isEqualTo("No operations provided");
    }

    @Test
    @DisplayName("non-array operations value returns 400 with PARSE_ERROR")
    @SuppressWarnings("unchecked")
    void nonArrayOperationsValue() {
        ResponseEntity<?> response = controller.executeOperations(
                Map.of("atomic:operations", "not-an-array"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> err = ((List<Map<String, Object>>) body.get("errors")).get(0);
        assertThat(err.get("status")).isEqualTo("400");
        assertThat(err.get("code")).isEqualTo("PARSE_ERROR");
        assertThat(err.get("detail")).asString().isNotEmpty();
    }
}
