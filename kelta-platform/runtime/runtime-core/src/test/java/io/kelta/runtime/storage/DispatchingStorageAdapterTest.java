package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DispatchingStorageAdapter")
class DispatchingStorageAdapterTest {

    @Mock
    private PhysicalTableStorageAdapter physical;

    @Mock
    private ExternalStorageAdapter externalRest;

    private DispatchingStorageAdapter dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(physical.storageType()).thenReturn("physical-table");
        lenient().when(externalRest.storageType()).thenReturn("external-rest");
        dispatcher = new DispatchingStorageAdapter(physical, List.of(externalRest));
    }

    private static CollectionDefinition collectionWith(StorageConfig storageConfig) {
        return new CollectionDefinitionBuilder()
                .name("orders")
                .addField(new FieldDefinitionBuilder().name("name").type(FieldType.STRING).nullable(false).build())
                .storageConfig(storageConfig)
                .build();
    }

    @Test
    @DisplayName("routes a collection with no adapterType to the physical adapter")
    void defaultsToPhysical() {
        CollectionDefinition def = collectionWith(StorageConfig.physicalTable("orders"));
        dispatcher.query(def, null);

        verify(physical).query(def, null);
    }

    @Test
    @DisplayName("routes an explicit physical-table adapterType to the physical adapter")
    void explicitPhysical() {
        CollectionDefinition def =
                collectionWith(new StorageConfig("orders", Map.of("adapterType", "physical-table")));

        dispatcher.getById(def, "r1");

        verify(physical).getById(def, "r1");
    }

    @Test
    @DisplayName("routes a matching external adapterType to the external adapter")
    void routesToExternal() {
        CollectionDefinition def =
                collectionWith(new StorageConfig("orders", Map.of("adapterType", "external-rest")));
        dispatcher.query(def, null);

        verify(externalRest).query(def, null);
    }

    @Test
    @DisplayName("falls back to the physical adapter for an unknown adapterType")
    void unknownTypeFallsBackToPhysical() {
        CollectionDefinition def =
                collectionWith(new StorageConfig("orders", Map.of("adapterType", "does-not-exist")));

        dispatcher.delete(def, "r1");

        verify(physical).delete(def, "r1");
    }

    @Test
    @DisplayName("reports its own storage type as 'dispatching'")
    void reportsDispatchingType() {
        assertThat(dispatcher.storageType()).isEqualTo("dispatching");
    }

    @Test
    @DisplayName("resolves the right adapter via adapterFor")
    void adapterForResolution() {
        CollectionDefinition physicalDef = collectionWith(StorageConfig.physicalTable("orders"));
        CollectionDefinition externalDef =
                collectionWith(new StorageConfig("orders", Map.of("adapterType", "external-rest")));

        assertThat(dispatcher.adapterFor(physicalDef)).isSameAs(physical);
        assertThat(dispatcher.adapterFor(externalDef)).isSameAs(externalRest);
    }
}
