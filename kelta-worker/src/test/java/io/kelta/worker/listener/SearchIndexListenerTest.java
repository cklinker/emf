package io.kelta.worker.listener;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CollectionLifecycleManager;
import io.kelta.worker.service.SearchIndexService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

@DisplayName("SearchIndexListener")
class SearchIndexListenerTest {

    private SearchIndexService searchIndexService;
    private CollectionLifecycleManager lifecycleManager;
    private CollectionRegistry collectionRegistry;
    private ObjectMapper objectMapper;
    private SearchIndexListener listener;

    @BeforeEach
    void setUp() {
        searchIndexService = mock(SearchIndexService.class);
        lifecycleManager = mock(CollectionLifecycleManager.class);
        collectionRegistry = mock(CollectionRegistry.class);
        objectMapper = new ObjectMapper();
        listener = new SearchIndexListener(searchIndexService, lifecycleManager,
                collectionRegistry, objectMapper);

        // User collections return a non-system definition
        CollectionDefinition userCollection = mock(CollectionDefinition.class);
        when(userCollection.systemCollection()).thenReturn(false);
        when(collectionRegistry.get("products")).thenReturn(userCollection);

        // System collections return a system definition
        CollectionDefinition systemCollection = mock(CollectionDefinition.class);
        when(systemCollection.systemCollection()).thenReturn(true);
        when(collectionRegistry.get("fields")).thenReturn(systemCollection);
    }

    @Nested
    @DisplayName("handleRecordChanged")
    class HandleRecordChanged {

        @Test
        @DisplayName("should index record on CREATED event")
        void shouldIndexOnCreated() throws Exception {
            when(lifecycleManager.getCollectionIdByName("products")).thenReturn("col-1");

            String message = buildMessage("tenant-1", "products", "rec-1", ChangeType.CREATED,
                    Map.of("name", "Widget A"));

            listener.handleRecordChanged(message);

            verify(searchIndexService).indexRecord(
                    eq("tenant-1"), eq("col-1"), eq("products"), eq("rec-1"),
                    argThat(data -> "Widget A".equals(data.get("name"))));
        }

        @Test
        @DisplayName("should index record on UPDATED event")
        void shouldIndexOnUpdated() throws Exception {
            when(lifecycleManager.getCollectionIdByName("products")).thenReturn("col-1");

            String message = buildMessage("tenant-1", "products", "rec-1", ChangeType.UPDATED,
                    Map.of("name", "Widget B"));

            listener.handleRecordChanged(message);

            verify(searchIndexService).indexRecord(
                    eq("tenant-1"), eq("col-1"), eq("products"), eq("rec-1"),
                    argThat(data -> "Widget B".equals(data.get("name"))));
        }

        @Test
        @DisplayName("should remove record on DELETED event")
        void shouldRemoveOnDeleted() throws Exception {
            String message = buildMessage("tenant-1", "products", "rec-1", ChangeType.DELETED, null);

            listener.handleRecordChanged(message);

            verify(searchIndexService).removeRecord("tenant-1", "products", "rec-1");
        }

        @Test
        @DisplayName("should skip system collections")
        void shouldSkipSystemCollections() throws Exception {
            String message = buildMessage("tenant-1", "fields", "field-1", ChangeType.CREATED,
                    Map.of("name", "email"));

            listener.handleRecordChanged(message);

            verifyNoInteractions(searchIndexService);
        }

        @Test
        @DisplayName("should skip events without tenant ID")
        void shouldSkipWithoutTenantId() throws Exception {
            String message = objectMapper.writeValueAsString(Map.of(
                    "payload", Map.of(
                            "collectionName", "products",
                            "recordId", "rec-1",
                            "changeType", "CREATED",
                            "data", Map.of("name", "Test"))));

            listener.handleRecordChanged(message);

            verifyNoInteractions(searchIndexService);
        }

        @Test
        @DisplayName("should handle malformed messages gracefully")
        void shouldHandleMalformedMessages() {
            listener.handleRecordChanged("not valid json");

            verifyNoInteractions(searchIndexService);
        }
    }

    /**
     * Builds a Kafka message in the PlatformEvent envelope format.
     */
    private String buildMessage(String tenantId, String collectionName, String recordId,
                                 ChangeType changeType, Map<String, Object> data) throws Exception {
        RecordChangedPayload payload = new RecordChangedPayload(
                collectionName, recordId, changeType, data, null, null);

        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("tenantId", tenantId);
        envelope.put("payload", payload);

        return objectMapper.writeValueAsString(envelope);
    }
}
