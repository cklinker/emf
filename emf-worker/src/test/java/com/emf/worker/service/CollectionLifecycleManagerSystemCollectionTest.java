package com.emf.worker.service;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.worker.config.WorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for system collection flag parsing in CollectionLifecycleManager.
 *
 * <p>Verifies that when the control plane returns collection data with
 * system collection flags (systemCollection, tenantScoped, readOnly,
 * immutableFields, columnMapping), the CollectionLifecycleManager correctly
 * parses them and creates a CollectionDefinition with the proper flags set.
 *
 * <p>Since buildCollectionDefinition is private, we test it indirectly through
 * the public initializeCollection method.
 */
class CollectionLifecycleManagerSystemCollectionTest {

    private CollectionRegistry collectionRegistry;
    private StorageAdapter storageAdapter;
    private RestTemplate restTemplate;
    private WorkerProperties workerProperties;
    private ObjectMapper objectMapper;
    private CollectionLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        collectionRegistry = mock(CollectionRegistry.class);
        storageAdapter = mock(StorageAdapter.class);
        restTemplate = mock(RestTemplate.class);
        workerProperties = new WorkerProperties();
        workerProperties.setControlPlaneUrl("http://localhost:8080");
        workerProperties.setId("worker-test");
        objectMapper = new ObjectMapper();

        lifecycleManager = new CollectionLifecycleManager(
                collectionRegistry, storageAdapter, restTemplate,
                workerProperties, objectMapper);
    }

    // ==================== Helper Methods ====================

    /**
     * Builds mock control plane response data for a system collection.
     */
    private Map<String, Object> buildSystemCollectionResponse(String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "coll-123");
        data.put("name", name);
        data.put("displayName", "System " + name);
        data.put("description", "A system collection");
        data.put("systemCollection", true);
        data.put("tenantScoped", true);
        data.put("readOnly", true);
        data.put("immutableFields", List.of("tenantId", "email"));
        data.put("columnMapping", Map.of("firstName", "first_name", "lastName", "last_name"));
        data.put("fields", List.of(
                Map.of("name", "firstName", "type", "STRING"),
                Map.of("name", "lastName", "type", "STRING"),
                Map.of("name", "email", "type", "STRING", "required", true)
        ));
        return data;
    }

    /**
     * Builds mock control plane response data for a standard (non-system) collection.
     */
    private Map<String, Object> buildNonSystemCollectionResponse(String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "coll-456");
        data.put("name", name);
        data.put("displayName", name);
        data.put("fields", List.of(
                Map.of("name", "title", "type", "STRING"),
                Map.of("name", "price", "type", "DOUBLE")
        ));
        return data;
    }

    /**
     * Sets up the RestTemplate mock to return the given response data for a collection.
     */
    @SuppressWarnings("unchecked")
    private void mockControlPlaneResponse(String collectionId, Map<String, Object> responseData) {
        String collectionUrl = "http://localhost:8080/control/collections/" + collectionId;
        ResponseEntity<Map> collectionResponse = new ResponseEntity<>(responseData, HttpStatus.OK);
        when(restTemplate.getForEntity(eq(collectionUrl), eq(Map.class)))
                .thenReturn(collectionResponse);

        // Mock the validation rules endpoint (returns empty list)
        String rulesUrl = "http://localhost:8080/control/collections/" + collectionId + "/validation-rules";
        ResponseEntity<List> rulesResponse = new ResponseEntity<>(List.of(), HttpStatus.OK);
        when(restTemplate.getForEntity(eq(rulesUrl), eq(List.class)))
                .thenReturn(rulesResponse);

        // Mock the ready notification
        String readyUrl = "http://localhost:8080/control/assignments/" + collectionId + "/worker-test/ready";
        when(restTemplate.postForEntity(eq(readyUrl), any(), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
    }

    // ==================== System Collection Flag Tests ====================

    @Nested
    @DisplayName("System Collection Flag Parsing")
    class SystemCollectionFlagTests {

        @Test
        @DisplayName("Should parse systemCollection=true from control plane response")
        void initializeCollection_parsesSystemCollectionFlag() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            // Capture the CollectionDefinition registered
            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertTrue(registeredDef.systemCollection(),
                    "Collection should have systemCollection=true");
        }

        @Test
        @DisplayName("Should parse tenantScoped=true from control plane response")
        void initializeCollection_parsesTenantScopedFlag() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertTrue(registeredDef.tenantScoped(),
                    "Collection should have tenantScoped=true");
        }

        @Test
        @DisplayName("Should parse readOnly=true from control plane response")
        void initializeCollection_parsesReadOnlyFlag() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertTrue(registeredDef.readOnly(),
                    "Collection should have readOnly=true");
        }

        @Test
        @DisplayName("Should parse immutableFields from control plane response")
        void initializeCollection_parsesImmutableFields() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            Set<String> immutableFields = registeredDef.immutableFields();
            assertNotNull(immutableFields, "immutableFields should not be null");
            assertEquals(2, immutableFields.size(),
                    "Should have 2 immutable fields");
            assertTrue(immutableFields.contains("tenantId"),
                    "immutableFields should contain 'tenantId'");
            assertTrue(immutableFields.contains("email"),
                    "immutableFields should contain 'email'");
        }

        @Test
        @DisplayName("Should parse columnMapping from control plane response")
        void initializeCollection_parsesColumnMapping() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            Map<String, String> columnMapping = registeredDef.columnMapping();
            assertNotNull(columnMapping, "columnMapping should not be null");
            assertEquals(2, columnMapping.size(),
                    "Should have 2 column mappings");
            assertEquals("first_name", columnMapping.get("firstName"),
                    "firstName should map to first_name");
            assertEquals("last_name", columnMapping.get("lastName"),
                    "lastName should map to last_name");
        }
    }

    // ==================== Default Values for Non-System Collections ====================

    @Nested
    @DisplayName("Default Values for Non-System Collections")
    class DefaultValueTests {

        @Test
        @DisplayName("Should default systemCollection to false when not present in response")
        void initializeCollection_defaultsSystemCollectionToFalse() {
            String collectionId = "coll-456";
            Map<String, Object> response = buildNonSystemCollectionResponse("products");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertFalse(registeredDef.systemCollection(),
                    "Non-system collection should default to systemCollection=false");
        }

        @Test
        @DisplayName("Should default readOnly to false when not present in response")
        void initializeCollection_defaultsReadOnlyToFalse() {
            String collectionId = "coll-456";
            Map<String, Object> response = buildNonSystemCollectionResponse("products");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertFalse(registeredDef.readOnly(),
                    "Non-system collection should default to readOnly=false");
        }

        @Test
        @DisplayName("Should default immutableFields to empty set when not present in response")
        void initializeCollection_defaultsImmutableFieldsToEmpty() {
            String collectionId = "coll-456";
            Map<String, Object> response = buildNonSystemCollectionResponse("products");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertNotNull(registeredDef.immutableFields());
            assertTrue(registeredDef.immutableFields().isEmpty(),
                    "Non-system collection should have empty immutableFields");
        }

        @Test
        @DisplayName("Should default columnMapping to empty map when not present in response")
        void initializeCollection_defaultsColumnMappingToEmpty() {
            String collectionId = "coll-456";
            Map<String, Object> response = buildNonSystemCollectionResponse("products");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertNotNull(registeredDef.columnMapping());
            assertTrue(registeredDef.columnMapping().isEmpty(),
                    "Non-system collection should have empty columnMapping");
        }
    }

    // ==================== Partial Flag Combinations ====================

    @Nested
    @DisplayName("Partial Flag Combinations")
    class PartialFlagTests {

        @Test
        @DisplayName("Should parse tenantScoped=false for global system collection")
        void initializeCollection_parsesTenantScopedFalse() {
            String collectionId = "coll-789";
            Map<String, Object> response = new HashMap<>();
            response.put("id", "coll-789");
            response.put("name", "global-config");
            response.put("systemCollection", true);
            response.put("tenantScoped", false);
            response.put("readOnly", false);
            response.put("fields", List.of(
                    Map.of("name", "key", "type", "STRING"),
                    Map.of("name", "value", "type", "STRING")
            ));
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertTrue(registeredDef.systemCollection(),
                    "Should be a system collection");
            assertFalse(registeredDef.tenantScoped(),
                    "Should have tenantScoped=false for global collections");
            assertFalse(registeredDef.readOnly(),
                    "Should have readOnly=false");
        }

        @Test
        @DisplayName("Should handle system collection with immutableFields but no columnMapping")
        void initializeCollection_handlesImmutableFieldsWithoutColumnMapping() {
            String collectionId = "coll-999";
            Map<String, Object> response = new HashMap<>();
            response.put("id", "coll-999");
            response.put("name", "sessions");
            response.put("systemCollection", true);
            response.put("tenantScoped", true);
            response.put("immutableFields", List.of("sessionToken"));
            response.put("fields", List.of(
                    Map.of("name", "sessionToken", "type", "STRING"),
                    Map.of("name", "userId", "type", "STRING")
            ));
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());

            CollectionDefinition registeredDef = defCaptor.getValue();
            assertEquals(1, registeredDef.immutableFields().size());
            assertTrue(registeredDef.immutableFields().contains("sessionToken"));
            assertTrue(registeredDef.columnMapping().isEmpty(),
                    "columnMapping should default to empty when not provided");
        }
    }

    // ==================== Storage Adapter Interaction ====================

    @Nested
    @DisplayName("Storage Adapter Interaction")
    class StorageAdapterTests {

        @Test
        @DisplayName("Should call storageAdapter.initializeCollection with the parsed definition")
        void initializeCollection_callsStorageAdapterWithParsedDefinition() {
            String collectionId = "coll-123";
            Map<String, Object> response = buildSystemCollectionResponse("users");
            mockControlPlaneResponse(collectionId, response);

            lifecycleManager.initializeCollection(collectionId);

            // Verify storageAdapter was called with the definition
            ArgumentCaptor<CollectionDefinition> defCaptor =
                    ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(storageAdapter).initializeCollection(defCaptor.capture());

            CollectionDefinition storageDef = defCaptor.getValue();
            assertEquals("users", storageDef.name());
            assertTrue(storageDef.systemCollection(),
                    "Definition passed to storage adapter should have systemCollection=true");
        }
    }
}
