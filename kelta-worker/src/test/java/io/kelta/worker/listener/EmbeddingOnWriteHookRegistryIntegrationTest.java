package io.kelta.worker.listener;

import io.kelta.runtime.embedding.EmbeddingService;
import io.kelta.runtime.embedding.HashingEmbeddingService;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying {@link EmbeddingOnWriteHook} is dispatched by a real
 * {@link BeforeSaveHookRegistry} as a wildcard before-save hook, using the same
 * {@code evaluateBeforeCreate}/{@code evaluateBeforeUpdate} entry points the query engine
 * calls. Exercises registration + dispatch + the field-update merge contract with the real
 * {@link HashingEmbeddingService}. Unit-level branch behavior is covered in
 * {@code EmbeddingOnWriteHookTest}.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingOnWriteHookRegistryIntegrationTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private CollectionRegistry collectionRegistry;

    private final EmbeddingService embeddingService = new HashingEmbeddingService();

    private BeforeSaveHookRegistry registry;

    private CollectionDefinition articles() {
        return new CollectionDefinitionBuilder().name("articles")
                .addField(FieldDefinition.requiredString("title"))
                .addField(new FieldDefinition("embedding", FieldType.VECTOR,
                        true, false, false, null, null, null, null,
                        Map.of("dimension", embeddingService.dimensions(), "embeddingSource", "title"),
                        null))
                .build();
    }

    private static Map<String, Object> record(String title) {
        Map<String, Object> record = new HashMap<>();
        record.put("__collectionName", "articles");
        record.put("title", title);
        return record;
    }

    @BeforeEach
    void setUp() {
        registry = new BeforeSaveHookRegistry();
        registry.register(new EmbeddingOnWriteHook(collectionRegistry, embeddingService,
                new io.kelta.worker.service.FieldMaskingService()));
        lenient().when(collectionRegistry.get("articles")).thenReturn(articles());
    }

    @Test
    void registeredAsWildcardHookForEveryCollection() {
        assertTrue(registry.hasHooks("articles"));
        assertTrue(registry.hasHooks("any-other-collection"));
    }

    @Test
    void beforeCreate_populatesVectorThroughRegistry() {
        Map<String, Object> record = record("machine learning");

        BeforeSaveResult result = registry.evaluateBeforeCreate("articles", record, TENANT_ID);

        assertThat(result.hasFieldUpdates()).isTrue();
        String expected = EmbeddingService.toVectorLiteral(embeddingService.embed("machine learning"));
        assertThat(result.getFieldUpdates()).containsEntry("embedding", expected);
        // The registry also merges updates back into the record the engine will persist.
        assertThat(record).containsEntry("embedding", expected);
    }

    @Test
    void beforeUpdate_withoutSourceInPayload_doesNotReembed() {
        Map<String, Object> record = new HashMap<>();
        record.put("__collectionName", "articles");
        record.put("someOtherField", "x");

        BeforeSaveResult result = registry.evaluateBeforeUpdate(
                "articles", "id-1", record, Map.of(), TENANT_ID);

        assertThat(result.hasFieldUpdates()).isFalse();
        assertThat(record).doesNotContainKey("embedding");
    }
}
