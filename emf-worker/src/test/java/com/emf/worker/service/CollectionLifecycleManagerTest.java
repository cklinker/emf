package com.emf.worker.service;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.worker.config.WorkerMetricsConfig;
import com.emf.worker.config.WorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionLifecycleManager")
class CollectionLifecycleManagerTest {

    @Mock private CollectionRegistry collectionRegistry;
    @Mock private StorageAdapter storageAdapter;
    @Mock private RestTemplate restTemplate;
    @Mock private WorkerMetricsConfig metricsConfig;

    private WorkerProperties workerProperties;
    private ObjectMapper objectMapper;
    private CollectionLifecycleManager manager;

    private static final String COLLECTION_ID = "col-123";
    private static final String CONTROL_PLANE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        workerProperties = new WorkerProperties();
        workerProperties.setControlPlaneUrl(CONTROL_PLANE_URL);
        workerProperties.setId("worker-test");
        objectMapper = new ObjectMapper();

        manager = new CollectionLifecycleManager(
                collectionRegistry, storageAdapter, restTemplate,
                workerProperties, objectMapper);
    }

    @Nested
    @DisplayName("initializeCollection")
    class InitializeCollectionTests {

        @Test
        @DisplayName("should fetch definition, register, and initialize storage")
        void successfulInitialization() {
            Map<String, Object> collectionData = Map.of(
                    "name", "accounts",
                    "displayName", "Accounts",
                    "description", "Customer accounts",
                    "storageMode", "PHYSICAL_TABLES",
                    "tableName", "tbl_accounts",
                    "fields", List.of(
                            Map.of("name", "name", "type", "STRING", "required", true),
                            Map.of("name", "email", "type", "STRING", "required", false)
                    )
            );

            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(collectionData, HttpStatus.OK));

            // Stub the readiness notification
            String readyUrl = CONTROL_PLANE_URL + "/control/assignments/" + COLLECTION_ID + "/worker-test/ready";
            when(restTemplate.postForEntity(eq(readyUrl), any(), eq(Void.class)))
                    .thenReturn(new ResponseEntity<>(HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            // Verify registration
            ArgumentCaptor<CollectionDefinition> defCaptor = ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(defCaptor.capture());
            CollectionDefinition captured = defCaptor.getValue();
            assertThat(captured.name()).isEqualTo("accounts");

            // Verify storage initialization
            verify(storageAdapter).initializeCollection(any(CollectionDefinition.class));

            // Verify it's tracked as active
            assertThat(manager.getActiveCollections()).contains(COLLECTION_ID);
            assertThat(manager.getActiveCollectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle failed HTTP response gracefully")
        void handleFailedResponse() {
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

            manager.initializeCollection(COLLECTION_ID);

            verify(collectionRegistry, never()).register(any());
            verify(storageAdapter, never()).initializeCollection(any());
            assertThat(manager.getActiveCollections()).isEmpty();
        }

        @Test
        @DisplayName("should handle null body in response")
        void handleNullBody() {
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            verify(collectionRegistry, never()).register(any());
            assertThat(manager.getActiveCollections()).isEmpty();
        }

        @Test
        @DisplayName("should handle missing collection name")
        void handleMissingName() {
            Map<String, Object> data = Map.of("description", "No name field");
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(data, HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            verify(collectionRegistry, never()).register(any());
            assertThat(manager.getActiveCollections()).isEmpty();
        }

        @Test
        @DisplayName("should handle exception during initialization")
        void handleException() {
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            manager.initializeCollection(COLLECTION_ID);

            verify(collectionRegistry, never()).register(any());
            assertThat(manager.getActiveCollections()).isEmpty();
        }

        @Test
        @DisplayName("should add default name field when no fields present")
        void addDefaultFieldWhenNoFields() {
            Map<String, Object> data = Map.of("name", "simple_collection");
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(data, HttpStatus.OK));

            String readyUrl = CONTROL_PLANE_URL + "/control/assignments/" + COLLECTION_ID + "/worker-test/ready";
            when(restTemplate.postForEntity(eq(readyUrl), any(), eq(Void.class)))
                    .thenReturn(new ResponseEntity<>(HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            ArgumentCaptor<CollectionDefinition> captor = ArgumentCaptor.forClass(CollectionDefinition.class);
            verify(collectionRegistry).register(captor.capture());
            assertThat(captor.getValue().fields()).isNotEmpty();
        }

        @Test
        @DisplayName("should update metrics when metricsConfig is set")
        void updateMetricsWhenConfigSet() {
            AtomicInteger counter = new AtomicInteger(0);
            when(metricsConfig.getInitializingCount()).thenReturn(counter);
            manager.setMetricsConfig(metricsConfig);

            // Force an early return with null body so we can check metrics bookkeeping
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            // Counter should be back to 0 (incremented then decremented in finally block)
            assertThat(counter.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("should skip fields with unknown type and default to STRING")
        void handleUnknownFieldType() {
            Map<String, Object> data = Map.of(
                    "name", "test_collection",
                    "fields", List.of(
                            Map.of("name", "custom_field", "type", "UNKNOWN_TYPE")
                    )
            );
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(data, HttpStatus.OK));

            String readyUrl = CONTROL_PLANE_URL + "/control/assignments/" + COLLECTION_ID + "/worker-test/ready";
            when(restTemplate.postForEntity(eq(readyUrl), any(), eq(Void.class)))
                    .thenReturn(new ResponseEntity<>(HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);

            verify(collectionRegistry).register(any(CollectionDefinition.class));
            assertThat(manager.getActiveCollections()).contains(COLLECTION_ID);
        }
    }

    @Nested
    @DisplayName("teardownCollection")
    class TeardownTests {

        @Test
        @DisplayName("should unregister and remove active collection")
        void teardownActiveCollection() {
            // First initialize a collection
            Map<String, Object> data = Map.of("name", "accounts");
            String fetchUrl = CONTROL_PLANE_URL + "/control/collections/" + COLLECTION_ID;
            when(restTemplate.getForEntity(eq(fetchUrl), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(data, HttpStatus.OK));
            String readyUrl = CONTROL_PLANE_URL + "/control/assignments/" + COLLECTION_ID + "/worker-test/ready";
            when(restTemplate.postForEntity(eq(readyUrl), any(), eq(Void.class)))
                    .thenReturn(new ResponseEntity<>(HttpStatus.OK));

            manager.initializeCollection(COLLECTION_ID);
            assertThat(manager.getActiveCollections()).contains(COLLECTION_ID);

            // Now tear it down
            manager.teardownCollection(COLLECTION_ID);

            verify(collectionRegistry).unregister("accounts");
            assertThat(manager.getActiveCollections()).isEmpty();
        }

        @Test
        @DisplayName("should handle teardown of unknown collection gracefully")
        void teardownUnknownCollection() {
            manager.teardownCollection("unknown-id");

            verify(collectionRegistry, never()).unregister(anyString());
            assertThat(manager.getActiveCollections()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Active collection tracking")
    class ActiveCollectionTests {

        @Test
        @DisplayName("should return empty set initially")
        void emptyInitially() {
            assertThat(manager.getActiveCollections()).isEmpty();
            assertThat(manager.getActiveCollectionCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return defensive copy of active collections")
        void defensiveCopy() {
            Set<String> result = manager.getActiveCollections();
            assertThat(result).isNotSameAs(manager.getActiveCollections());
        }
    }
}
