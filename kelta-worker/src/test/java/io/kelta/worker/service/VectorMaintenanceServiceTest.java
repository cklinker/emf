package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@DisplayName("VectorMaintenanceService")
class VectorMaintenanceServiceTest {

    private CollectionRegistry collectionRegistry;
    private StorageAdapter storageAdapter;
    private VectorMaintenanceService service;

    @BeforeEach
    void setUp() {
        collectionRegistry = mock(CollectionRegistry.class);
        storageAdapter = mock(StorageAdapter.class);
        service = new VectorMaintenanceService(collectionRegistry, storageAdapter);
    }

    private static FieldDefinition vector(String name, String source) {
        Map<String, Object> config = new HashMap<>();
        config.put("dimension", 384);
        if (source != null) {
            config.put("embeddingSource", source);
        }
        return new FieldDefinition(name, FieldType.VECTOR,
                true, false, false, null, null, null, null, config, null);
    }

    @Test
    @DisplayName("clears only VECTOR fields whose embeddingSource is the toggled field")
    void purgesMatchingVectors() {
        CollectionDefinition def = new CollectionDefinitionBuilder().name("people")
                .addField(FieldDefinition.requiredString("ssn"))
                .addField(FieldDefinition.requiredString("notes"))
                .addField(vector("ssn_vec", "ssn"))
                .addField(vector("notes_vec", "notes"))
                .build();
        when(collectionRegistry.get("people")).thenReturn(def);

        service.purgeVectorsForSourceAsync("tenant-1", "people", "ssn");

        verify(storageAdapter).clearVectorColumn(def, "ssn_vec");
        verify(storageAdapter, never()).clearVectorColumn(def, "notes_vec");
    }

    @Test
    @DisplayName("no-op when the collection is not registered")
    void noopWhenCollectionMissing() {
        when(collectionRegistry.get("gone")).thenReturn(null);

        service.purgeVectorsForSourceAsync("tenant-1", "gone", "ssn");

        verifyNoInteractions(storageAdapter);
    }

    @Test
    @DisplayName("no-op for null arguments")
    void noopForNullArgs() {
        service.purgeVectorsForSourceAsync(null, "people", "ssn");

        verifyNoInteractions(storageAdapter, collectionRegistry);
    }
}
