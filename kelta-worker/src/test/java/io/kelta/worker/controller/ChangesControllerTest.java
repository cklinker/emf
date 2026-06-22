package io.kelta.worker.controller;

import io.kelta.worker.service.ChangesService;
import io.kelta.worker.service.ChangesService.ChangesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangesController")
class ChangesControllerTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private ChangesService changesService;

    private ChangesController controller;

    @BeforeEach
    void setUp() {
        controller = new ChangesController(changesService);
    }

    @Test
    @DisplayName("returns the changes feed wrapped in a data envelope")
    void returnsChanges() {
        when(changesService.changes(eq(TENANT), eq("orders"), eq(Instant.parse("2026-06-01T00:00:00Z"))))
                .thenReturn(Map.of("deletions", List.of("r1"), "cursor", "2026-06-21T00:00:00Z"));

        ResponseEntity<Map<String, Object>> response =
                controller.changes(TENANT, "orders", "2026-06-01T00:00:00Z");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }

    @Test
    @DisplayName("treats an absent cursor as an initial sync")
    void absentCursor() {
        when(changesService.changes(eq(TENANT), eq("orders"), isNull()))
                .thenReturn(Map.of("deletions", List.of(), "cursor", "2026-06-21T00:00:00Z"));

        ResponseEntity<Map<String, Object>> response = controller.changes(TENANT, "orders", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(changesService).changes(eq(TENANT), eq("orders"), isNull());
    }

    @Test
    @DisplayName("rejects a non-ISO cursor with 400")
    void invalidCursor() {
        ResponseEntity<Map<String, Object>> response = controller.changes(TENANT, "orders", "not-a-date");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(changesService);
    }

    @Test
    @DisplayName("maps an unknown collection to 404")
    void unknownCollection() {
        when(changesService.changes(eq(TENANT), eq("nope"), isNull()))
                .thenThrow(new ChangesException("Unknown collection: nope"));

        ResponseEntity<Map<String, Object>> response = controller.changes(TENANT, "nope", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
