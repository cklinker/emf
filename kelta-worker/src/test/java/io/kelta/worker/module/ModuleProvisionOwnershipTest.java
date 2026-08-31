package io.kelta.worker.module;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.module.ModuleManifest;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provisioning must distinguish what a module created from what it merely reused.
 *
 * <p>Uninstall may remove a collection the module created; it must never remove one that already
 * existed and was adopted, because that is a tenant's own data. Returning only the created names —
 * as this did — throws that distinction away, which is why uninstall could only ever safely remove
 * nothing.
 */
@DisplayName("ModuleCollectionProvisioner — ownership")
class ModuleProvisionOwnershipTest {

    private static final String TENANT = "tenant-1";

    private CollectionRegistry registry;
    private ModuleCollectionProvisioner provisioner;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        QueryEngine queryEngine = mock(QueryEngine.class);
        CollectionDefinition systemDef = mock(CollectionDefinition.class);
        when(registry.get("collections")).thenReturn(systemDef);
        when(registry.get("fields")).thenReturn(systemDef);
        when(queryEngine.create(any(), any())).thenReturn(Map.of("id", "new-id"));
        provisioner = new ModuleCollectionProvisioner(queryEngine, registry);
    }

    private static ModuleManifest.CollectionManifest collection(String name) {
        return new ModuleManifest.CollectionManifest(name, name, List.of(
            new ModuleManifest.CollectionManifest.FieldManifest(
                "reference", "Reference", "STRING", false)));
    }

    @Test
    @DisplayName("a collection the module creates is CREATED; one that already exists is ADOPTED")
    void separatesCreatedFromAdopted() {
        // "existing" is already in the registry — a tenant collection the module happens to name.
        when(registry.get("existing")).thenReturn(mock(CollectionDefinition.class));
        when(registry.get("brand_new")).thenReturn(null);

        var result = provisioner.provisionWithOwnership(TENANT,
            List.of(collection("brand_new"), collection("existing")));

        assertThat(result.created()).containsExactly("brand_new");
        assertThat(result.adopted()).containsExactly("existing");
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("the deprecated accessor still reports only what was created")
    void deprecatedAccessorIsUnchanged() {
        when(registry.get(anyString())).thenReturn(null);
        when(registry.get("collections")).thenReturn(mock(CollectionDefinition.class));
        when(registry.get("fields")).thenReturn(mock(CollectionDefinition.class));

        assertThat(provisioner.provision(TENANT, List.of(collection("a"))))
                .containsExactly("a");
    }

    @Test
    @DisplayName("nothing declared provisions nothing, rather than failing")
    void emptyIsFine() {
        var result = provisioner.provisionWithOwnership(TENANT, List.of());

        assertThat(result.created()).isEmpty();
        assertThat(result.adopted()).isEmpty();
    }
}
