package io.kelta.worker.module;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.module.ModuleManifest.CollectionManifest;
import io.kelta.runtime.module.ModuleManifest.CollectionManifest.FieldManifest;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ModuleCollectionProvisioner")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModuleCollectionProvisionerTest {

    private static final String TENANT = "tenant-1";

    @Mock private QueryEngine queryEngine;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private CollectionDefinition collectionsDef;
    @Mock private CollectionDefinition fieldsDef;

    private ModuleCollectionProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new ModuleCollectionProvisioner(queryEngine, collectionRegistry);
        when(collectionRegistry.get("collections")).thenReturn(collectionsDef);
        when(collectionRegistry.get("fields")).thenReturn(fieldsDef);
        when(queryEngine.create(eq(collectionsDef), any())).thenReturn(Map.of("id", "col-1"));
    }

    private CollectionManifest invoices() {
        return new CollectionManifest("invoices", "Invoices", List.of(
                new FieldManifest("reference", "Reference", "STRING", true),
                new FieldManifest("amount", null, "DECIMAL", false)));
    }

    @Test
    @DisplayName("Creates the collection and its fields through the standard write path")
    void createsCollectionAndFields() {
        List<String> created = provisioner.provision(TENANT, List.of(invoices()));

        assertThat(created).containsExactly("invoices");

        ArgumentCaptor<Map<String, Object>> collection = ArgumentCaptor.captor();
        verify(queryEngine).create(eq(collectionsDef), collection.capture());
        // tenantId must be set explicitly — collection.tenant_id is NOT NULL and only the
        // JSON:API layer injects it, which this direct create bypasses.
        assertThat(collection.getValue())
                .containsEntry("tenantId", TENANT)
                .containsEntry("name", "invoices")
                .containsEntry("displayName", "Invoices")
                .containsEntry("path", "/api/invoices")
                .containsEntry("systemCollection", false);

        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.captor();
        verify(queryEngine, times(2)).create(eq(fieldsDef), fields.capture());
        assertThat(fields.getAllValues().get(0))
                .containsEntry("collectionId", "col-1")
                .containsEntry("name", "reference")
                .containsEntry("type", "STRING")
                .containsEntry("required", true)
                .containsEntry("fieldOrder", 0);
        // An omitted displayName falls back to the field name.
        assertThat(fields.getAllValues().get(1))
                .containsEntry("displayName", "amount")
                .containsEntry("required", false)
                .containsEntry("fieldOrder", 1);
    }

    @Test
    @DisplayName("Defaults the display name from the collection name")
    void defaultsDisplayName() {
        provisioner.provision(TENANT, List.of(
                new CollectionManifest("invoices", null, List.of())));

        ArgumentCaptor<Map<String, Object>> collection = ArgumentCaptor.captor();
        verify(queryEngine).create(eq(collectionsDef), collection.capture());
        assertThat(collection.getValue()).containsEntry("displayName", "Invoices");
    }

    @Test
    @DisplayName("Skips a collection that already exists rather than clobbering it")
    void skipsExistingCollection() {
        when(collectionRegistry.get("invoices")).thenReturn(collectionsDef);

        List<String> created = provisioner.provision(TENANT, List.of(invoices()));

        assertThat(created).isEmpty();
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("Rejects a malformed manifest before creating anything")
    void rejectsMalformedManifestBeforeCreating() {
        List<CollectionManifest> collections = List.of(
                invoices(),
                new CollectionManifest("Not Valid", null, List.of()));

        assertThatThrownBy(() -> provisioner.provision(TENANT, collections))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not Valid");

        // The valid first entry must not have been created — a bad manifest cannot
        // half-provision a tenant.
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("Rejects a field name the platform reserves")
    void rejectsReservedFieldName() {
        List<CollectionManifest> collections = List.of(new CollectionManifest(
                "invoices", null, List.of(new FieldManifest("tenantId", null, "STRING", false))));

        assertThatThrownBy(() -> provisioner.provision(TENANT, collections))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("Does nothing when the manifest declares no collections")
    void noCollectionsIsANoOp() {
        assertThat(provisioner.provision(TENANT, List.of())).isEmpty();
        assertThat(provisioner.provision(TENANT, null)).isEmpty();
        verify(queryEngine, never()).create(any(), any());
    }
}
