package io.kelta.worker.service;

import io.kelta.runtime.embedding.EmbeddingService;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import io.kelta.worker.service.SemanticSearchService.SemanticSearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchService")
class SemanticSearchServiceTest {

    @Mock private StorageAdapter storageAdapter;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private EmbeddingService embeddingService;

    private SemanticSearchService service;

    private static FieldDefinition vectorField(int dimension) {
        return new FieldDefinition("embedding", FieldType.VECTOR,
                true, false, false, null, null, null, null, Map.of("dimension", dimension), null);
    }

    private static CollectionDefinition collection(FieldDefinition... fields) {
        CollectionDefinitionBuilder b = new CollectionDefinitionBuilder().name("docs");
        for (FieldDefinition f : fields) {
            b.addField(f);
        }
        return b.build();
    }

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(storageAdapter, collectionRegistry, embeddingService);
        lenient().when(embeddingService.dimensions()).thenReturn(384);
        lenient().when(embeddingService.providerId()).thenReturn("hashing-v1-d384");
        lenient().when(embeddingService.embed(anyString())).thenReturn(new float[384]);
    }

    @Test
    @DisplayName("ranks records and returns distances in meta, stripping the raw vector")
    void happyPath() {
        when(collectionRegistry.get("docs"))
                .thenReturn(collection(vectorField(384), FieldDefinition.requiredString("title")));
        when(storageAdapter.semanticSearch(any(), eq("embedding"), anyString(), eq(10), anyList()))
                .thenReturn(new ArrayList<>(List.of(
                        new HashMap<>(Map.of("id", "r1", "title", "Hello",
                                "embedding", "[0.0]", "_distance", 0.05)))));

        Map<String, Object> result = service.search("docs", "hello", 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertThat(data).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) data.get(0).get("attributes");
        assertThat(attrs).containsKey("title").doesNotContainKeys("embedding", "_distance");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> distances = (Map<String, Object>) meta.get("distances");
        assertThat(distances).containsEntry("r1", 0.05);
    }

    @Test
    @DisplayName("rejects a blank query")
    void blankQuery() {
        assertThatThrownBy(() -> service.search("docs", "  ", 10))
                .isInstanceOf(SemanticSearchException.class);
    }

    @Test
    @DisplayName("rejects an unknown collection")
    void unknownCollection() {
        when(collectionRegistry.get("ghost")).thenReturn(null);
        assertThatThrownBy(() -> service.search("ghost", "hi", 10))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessageContaining("Unknown collection");
    }

    @Test
    @DisplayName("rejects a collection with no VECTOR field")
    void noVectorField() {
        when(collectionRegistry.get("docs")).thenReturn(collection(FieldDefinition.requiredString("title")));
        assertThatThrownBy(() -> service.search("docs", "hi", 10))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessageContaining("no VECTOR field");
    }

    @Test
    @DisplayName("rejects a dimension mismatch between the field and the embedding provider")
    void dimensionMismatch() {
        when(collectionRegistry.get("docs")).thenReturn(collection(vectorField(768)));
        assertThatThrownBy(() -> service.search("docs", "hi", 10))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    @DisplayName("clamps the limit to the maximum")
    void clampsLimit() {
        when(collectionRegistry.get("docs")).thenReturn(collection(vectorField(384)));
        when(storageAdapter.semanticSearch(any(), anyString(), anyString(), eq(100), anyList()))
                .thenReturn(new ArrayList<>());

        service.search("docs", "hi", 5000);
        // verified via the eq(100) stub matching — no exception means the clamp held
    }
}
