package io.kelta.worker.listener;

import io.kelta.runtime.embedding.EmbeddingService;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingOnWriteHook")
class EmbeddingOnWriteHookTest {

    private static final int DIM = 384;
    private static final float[] VEC = {0.1f, 0.2f, 0.3f};

    @Mock
    private CollectionRegistry collectionRegistry;

    @Mock
    private EmbeddingService embeddingService;

    private EmbeddingOnWriteHook hook;

    /** Collection with a title field and a VECTOR embedding field sourced from "title". */
    private static CollectionDefinition articles(String embeddingSource) {
        Map<String, Object> config = new HashMap<>();
        config.put("dimension", DIM);
        if (embeddingSource != null) {
            config.put("embeddingSource", embeddingSource);
        }
        return new CollectionDefinitionBuilder().name("articles")
                .addField(FieldDefinition.requiredString("title"))
                .addField(new FieldDefinition("embedding", FieldType.VECTOR,
                        true, false, false, null, null, null, null, config, null))
                .build();
    }

    private static Map<String, Object> record(Map<String, Object> fields) {
        Map<String, Object> record = new HashMap<>(fields);
        record.put("__collectionName", "articles");
        return record;
    }

    @BeforeEach
    void setUp() {
        hook = new EmbeddingOnWriteHook(collectionRegistry, embeddingService,
                new io.kelta.worker.service.FieldMaskingService());
        lenient().when(embeddingService.dimensions()).thenReturn(DIM);
        lenient().when(embeddingService.providerId()).thenReturn("test");
        lenient().when(embeddingService.embed(anyString())).thenReturn(VEC);
    }

    @Test
    @DisplayName("create: embeds the source field into the VECTOR field")
    void createEmbedsSource() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));

        BeforeSaveResult result = hook.beforeCreate(
                record(Map.of("title", "hello world")), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isTrue();
        assertThat(result.getFieldUpdates())
                .containsEntry("embedding", EmbeddingService.toVectorLiteral(VEC));
        verify(embeddingService).embed("hello world");
    }

    @Test
    @DisplayName("create: masking-configured source field is not embedded")
    void createSkipsMaskedSource() {
        // Source "title" carries a masking config — embedding it would make the
        // plaintext semantically searchable, bypassing masking.
        Map<String, Object> vecConfig = new HashMap<>();
        vecConfig.put("dimension", DIM);
        vecConfig.put("embeddingSource", "title");
        FieldDefinition maskedTitle = new io.kelta.runtime.model.FieldDefinitionBuilder()
                .name("title").type(FieldType.STRING)
                .fieldTypeConfig(Map.of("masking", Map.of("type", "FULL")))
                .build();
        CollectionDefinition def = new CollectionDefinitionBuilder().name("articles")
                .addField(maskedTitle)
                .addField(new FieldDefinition("embedding", FieldType.VECTOR,
                        true, false, false, null, null, null, null, vecConfig, null))
                .build();
        when(collectionRegistry.get("articles")).thenReturn(def);

        BeforeSaveResult result = hook.beforeCreate(
                record(Map.of("title", "secret value")), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("create: no source value → no updates")
    void createNoSourceValue() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));

        BeforeSaveResult result = hook.beforeCreate(record(new HashMap<>()), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("create: caller-supplied vector is not overwritten")
    void createExplicitVectorWins() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("title", "hello");
        fields.put("embedding", "[9,9,9]");
        BeforeSaveResult result = hook.beforeCreate(record(fields), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("update: source field absent from payload → no re-embed")
    void updateSkipsWhenSourceUnchanged() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));

        BeforeSaveResult result = hook.beforeUpdate(
                "id-1", record(Map.of("someOtherField", "x")), Map.of(), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("update: source field present → re-embeds")
    void updateReembedsWhenSourceChanged() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));

        BeforeSaveResult result = hook.beforeUpdate(
                "id-1", record(Map.of("title", "new title")), Map.of(), "tenant-1");

        assertThat(result.getFieldUpdates())
                .containsEntry("embedding", EmbeddingService.toVectorLiteral(VEC));
        verify(embeddingService).embed("new title");
    }

    @Test
    @DisplayName("dimension mismatch → field skipped with no update")
    void dimensionMismatchSkips() {
        when(collectionRegistry.get("articles")).thenReturn(articles("title"));
        when(embeddingService.dimensions()).thenReturn(128); // != field's 384

        BeforeSaveResult result = hook.beforeCreate(
                record(Map.of("title", "hello")), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("VECTOR field without embeddingSource config → untouched")
    void noSourceConfigSkips() {
        when(collectionRegistry.get("articles")).thenReturn(articles(null));

        BeforeSaveResult result = hook.beforeCreate(
                record(Map.of("title", "hello")), "tenant-1");

        assertThat(result.hasFieldUpdates()).isFalse();
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("unknown collection → ok, no updates")
    void unknownCollection() {
        when(collectionRegistry.get("articles")).thenReturn(null);

        BeforeSaveResult result = hook.beforeCreate(
                record(Map.of("title", "hello")), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isFalse();
    }

    @Test
    @DisplayName("missing __collectionName → ok, no lookup")
    void missingCollectionName() {
        BeforeSaveResult result = hook.beforeCreate(new HashMap<>(Map.of("title", "x")), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isFalse();
    }
}
