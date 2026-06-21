package io.kelta.worker.controller;

import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.service.MetadataDependencyService;
import io.kelta.worker.service.MetadataDependencyService.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetadataDependencyController")
class MetadataDependencyControllerTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private MetadataDependencyService service;

    private MetadataDependencyController controller;

    @BeforeEach
    void setUp() {
        controller = new MetadataDependencyController(service);
    }

    @Test
    @DisplayName("impact wraps the service result in a data envelope (no attributes -> FLS-safe)")
    void impactReturnsWrappedResult() {
        when(service.impact(eq(TENANT), eq(MetadataType.COLLECTION), eq("account"), eq(Direction.DEPENDENTS)))
                .thenReturn(Map.of("node", Map.of("id", "account"), "transitive", java.util.List.of()));

        ResponseEntity<Map<String, Object>> response =
                controller.impact(TENANT, "collection", "account", "dependents");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        // FLS-safe: the envelope's data carries no "attributes"/"relationships" keys.
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).doesNotContainKeys("attributes", "relationships");
    }

    @Test
    @DisplayName("impact accepts the dependencies direction")
    void impactDependenciesDirection() {
        when(service.impact(eq(TENANT), eq(MetadataType.FIELD), eq("f1"), eq(Direction.DEPENDENCIES)))
                .thenReturn(Map.of("node", Map.of("id", "f1")));

        controller.impact(TENANT, "field", "f1", "dependencies");

        verify(service).impact(eq(TENANT), eq(MetadataType.FIELD), eq("f1"), eq(Direction.DEPENDENCIES));
    }

    @Test
    @DisplayName("impact rejects an unknown metadata type with 400")
    void impactInvalidType() {
        ResponseEntity<Map<String, Object>> response =
                controller.impact(TENANT, "not-a-type", "x", "dependents");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("errors");
    }

    @Test
    @DisplayName("graph wraps the graph summary in a data envelope")
    void graphReturnsWrappedResult() {
        when(service.graphSummary(TENANT)).thenReturn(Map.of("nodeCount", 3, "hasCycle", false));

        ResponseEntity<Map<String, Object>> response = controller.graph(TENANT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
    }
}
