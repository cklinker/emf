package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.service.WorkerApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCollectionSchemaHandler")
class GetCollectionSchemaHandlerTest {

    @Mock
    private WorkerApiClient workerApiClient;

    private GetCollectionSchemaHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetCollectionSchemaHandler(workerApiClient);
    }

    @Test
    @DisplayName("returns collection schema with fields summary")
    @SuppressWarnings("unchecked")
    void returnsSchema() {
        Map<String, Object> collection = Map.of(
                "id", "col-1",
                "attributes", Map.of(
                        "name", "products",
                        "displayName", "Products",
                        "description", "Sellable items",
                        "displayFieldName", "name"
                )
        );
        when(workerApiClient.getCollectionByName("tenant-1", "products"))
                .thenReturn(Optional.of(collection));

        Map<String, Object> field = Map.of(
                "id", "f1",
                "attributes", Map.of(
                        "name", "sku",
                        "type", "STRING",
                        "required", true,
                        "uniqueConstraint", true
                )
        );
        when(workerApiClient.listFields("tenant-1", "col-1")).thenReturn(List.of(field));

        Object result = handler.execute("tenant-1", "user-1", Map.of("collectionName", "products"));

        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("products");
        assertThat(map.get("displayName")).isEqualTo("Products");
        assertThat(map.get("fieldCount")).isEqualTo(1);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) map.get("fields");
        assertThat(fields).hasSize(1);
        assertThat(fields.getFirst()).containsEntry("name", "sku");
        assertThat(fields.getFirst()).containsEntry("type", "STRING");
        assertThat(fields.getFirst()).containsEntry("required", true);
        assertThat(fields.getFirst()).containsEntry("unique", true);
    }

    @Test
    @DisplayName("throws when collectionName missing")
    void requiresName() {
        assertThatThrownBy(() -> handler.execute("tenant-1", "user-1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionName");
    }

    @Test
    @DisplayName("throws when collection not found")
    void throwsWhenMissing() {
        when(workerApiClient.getCollectionByName("tenant-1", "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.execute("tenant-1", "user-1", Map.of("collectionName", "missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collection not found");
    }

    @Test
    @DisplayName("declares get_collection_schema as its name")
    void declaresName() {
        assertThat(handler.name()).isEqualTo("get_collection_schema");
        assertThat(handler.inputSchema()).containsKey("properties");
    }
}
