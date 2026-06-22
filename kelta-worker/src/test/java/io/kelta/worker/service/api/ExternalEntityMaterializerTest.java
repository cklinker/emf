package io.kelta.worker.service.api;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.module.integration.api.ApiOperation;
import io.kelta.runtime.module.integration.api.ApiSpec;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.ApiOperationRepository;
import io.kelta.worker.service.api.ExternalEntityMaterializer.MaterializeRequest;
import io.kelta.worker.service.api.ExternalEntityMaterializer.MaterializeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ExternalEntityMaterializer")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalEntityMaterializerTest {

    private static final String TENANT = "tenant-1";

    @Mock private QueryEngine queryEngine;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private ApiSpecService specService;
    @Mock private ApiOperationRepository operationRepository;
    @Mock private CollectionDefinition collectionsDef;
    @Mock private CollectionDefinition fieldsDef;

    private final ObjectMapper json = new ObjectMapper();
    private ExternalEntityMaterializer materializer;

    @BeforeEach
    void setUp() {
        materializer = new ExternalEntityMaterializer(
                queryEngine, collectionRegistry, specService,
                operationRepository, new OpenApiCollectionMapper());
        when(collectionRegistry.get("collections")).thenReturn(collectionsDef);
        when(collectionRegistry.get("fields")).thenReturn(fieldsDef);
    }

    private ApiSpec spec(String baseUrl) {
        return new ApiSpec("spec-1", TENANT, "Petstore", null, "3.0.0", "Petstore", "1.0",
                baseUrl, null, null, "INLINE_JSON", null, "{}", "json", null,
                "hash", 1, true, Instant.EPOCH);
    }

    private ApiOperation getOp(String method, String responseJson) {
        JsonNode responses = json.readTree(responseJson);
        return new ApiOperation("op-1", TENANT, "spec-1", "listPets", "get__pets",
                method, "/pets", "List pets", null, null, null, null, responses, null, false);
    }

    private static final String PETS_RESPONSE = """
        {"200": {"content": {"application/json": {"schema": {
          "type": "array",
          "items": {"type": "object", "properties": {
            "id": {"type": "string"},
            "name": {"type": "string"},
            "age": {"type": "integer"}
          }}
        }}}}}
        """;

    @Test
    @DisplayName("creates an external-rest collection + a field per derived property")
    void materializesCollectionAndFields() {
        when(specService.findById("spec-1", TENANT)).thenReturn(Optional.of(spec("https://api.pet/v1")));
        when(operationRepository.findOperation(TENANT, "spec-1", "get__pets"))
                .thenReturn(Optional.of(getOp("GET", PETS_RESPONSE)));
        when(collectionRegistry.get("pets")).thenReturn(null);
        when(queryEngine.create(eq(collectionsDef), any())).thenReturn(Map.of("id", "col-1"));
        when(queryEngine.create(eq(fieldsDef), any())).thenReturn(Map.of("id", "f"));

        MaterializeResult result = materializer.materialize("spec-1", "get__pets",
                new MaterializeRequest("pets", null, null, null, null), TENANT);

        assertThat(result.collectionId()).isEqualTo("col-1");
        assertThat(result.collectionName()).isEqualTo("pets");
        assertThat(result.fieldCount()).isEqualTo(3);

        // Collection created once with an external-rest adapterConfig.
        ArgumentCaptor<Map<String, Object>> collCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).create(eq(collectionsDef), collCaptor.capture());
        Map<String, Object> data = collCaptor.getValue();
        assertThat(data).containsEntry("name", "pets").containsEntry("path", "/api/pets");
        @SuppressWarnings("unchecked")
        Map<String, Object> adapter = (Map<String, Object>) data.get("adapterConfig");
        assertThat(adapter)
                .containsEntry("adapterType", "external-rest")
                .containsEntry("baseUrl", "https://api.pet/v1")
                .containsEntry("path", "/pets")
                .containsEntry("idAttribute", "id");

        // One field record per derived property.
        verify(queryEngine, times(3)).create(eq(fieldsDef), any());
    }

    @Test
    @DisplayName("rejects a non-GET operation")
    void rejectsNonGet() {
        when(specService.findById("spec-1", TENANT)).thenReturn(Optional.of(spec("https://api.pet/v1")));
        when(operationRepository.findOperation(TENANT, "spec-1", "post__pets"))
                .thenReturn(Optional.of(getOp("POST", PETS_RESPONSE)));

        assertThatThrownBy(() -> materializer.materialize("spec-1", "post__pets",
                new MaterializeRequest("pets", null, null, null, null), TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only GET");
        verify(queryEngine, never()).create(any(), any());
    }

    @Test
    @DisplayName("rejects an invalid collection name")
    void rejectsBadName() {
        assertThatThrownBy(() -> materializer.materialize("spec-1", "get__pets",
                new MaterializeRequest("Bad Name", null, null, null, null), TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionName");
    }

    @Test
    @DisplayName("rejects when the collection already exists")
    void rejectsExisting() {
        when(specService.findById("spec-1", TENANT)).thenReturn(Optional.of(spec("https://api.pet/v1")));
        when(operationRepository.findOperation(TENANT, "spec-1", "get__pets"))
                .thenReturn(Optional.of(getOp("GET", PETS_RESPONSE)));
        when(collectionRegistry.get("pets")).thenReturn(mock(CollectionDefinition.class));

        assertThatThrownBy(() -> materializer.materialize("spec-1", "get__pets",
                new MaterializeRequest("pets", null, null, null, null), TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("rejects when the spec has no base URL")
    void rejectsNoBaseUrl() {
        when(specService.findById("spec-1", TENANT)).thenReturn(Optional.of(spec("  ")));

        assertThatThrownBy(() -> materializer.materialize("spec-1", "get__pets",
                new MaterializeRequest("pets", null, null, null, null), TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base URL");
    }

    @Test
    @DisplayName("honors an explicit dataPath and idAttribute override")
    void honorsOverrides() {
        when(specService.findById("spec-1", TENANT)).thenReturn(Optional.of(spec("https://api.pet/v1")));
        when(operationRepository.findOperation(TENANT, "spec-1", "get__pets"))
                .thenReturn(Optional.of(getOp("GET", PETS_RESPONSE)));
        when(collectionRegistry.get("pets")).thenReturn(null);
        when(queryEngine.create(eq(collectionsDef), any())).thenReturn(Map.of("id", "col-1"));
        when(queryEngine.create(eq(fieldsDef), any())).thenReturn(Map.of("id", "f"));

        materializer.materialize("spec-1", "get__pets",
                new MaterializeRequest("pets", "Pets", "results", "name", "cred-1"), TENANT);

        ArgumentCaptor<Map<String, Object>> collCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryEngine).create(eq(collectionsDef), collCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> adapter = (Map<String, Object>) collCaptor.getValue().get("adapterConfig");
        assertThat(adapter)
                .containsEntry("dataPath", "results")
                .containsEntry("idAttribute", "name")
                .containsEntry("credentialRef", "cred-1");
        assertThat(collCaptor.getValue()).containsEntry("displayName", "Pets");
    }
}
