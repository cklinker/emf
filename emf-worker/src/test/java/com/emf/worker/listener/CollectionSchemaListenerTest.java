package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.worker.service.CollectionLifecycleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.*;

@DisplayName("CollectionSchemaListener")
class CollectionSchemaListenerTest {

    private CollectionLifecycleManager lifecycleManager;
    private ObjectMapper objectMapper;
    private CollectionSchemaListener listener;

    @BeforeEach
    void setUp() {
        lifecycleManager = mock(CollectionLifecycleManager.class);
        objectMapper = new ObjectMapper();
        listener = new CollectionSchemaListener(lifecycleManager, objectMapper);
    }

    @Nested
    @DisplayName("CREATED events")
    class CreatedEvents {

        @Test
        @DisplayName("Should initialize new collection on CREATED event")
        void shouldInitializeNewCollection() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of());

            String message = buildMessage("col-1", "orders", ChangeType.CREATED);
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).initializeCollection("col-1");
            verify(lifecycleManager, never()).refreshCollection(any());
        }

        @Test
        @DisplayName("Should skip already active collection on CREATED event")
        void shouldSkipAlreadyActiveCollection() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of("col-1"));

            String message = buildMessage("col-1", "orders", ChangeType.CREATED);
            listener.handleCollectionChanged(message);

            verify(lifecycleManager, never()).initializeCollection(any());
            verify(lifecycleManager, never()).refreshCollection(any());
        }
    }

    @Nested
    @DisplayName("UPDATED events")
    class UpdatedEvents {

        @Test
        @DisplayName("Should refresh active collection on UPDATED event")
        void shouldRefreshActiveCollection() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of("col-1"));

            String message = buildMessage("col-1", "orders", ChangeType.UPDATED);
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).refreshCollection("col-1");
            verify(lifecycleManager, never()).initializeCollection(any());
        }

        @Test
        @DisplayName("Should initialize unknown collection on UPDATED event")
        void shouldInitializeUnknownCollectionOnUpdate() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of());

            String message = buildMessage("col-1", "orders", ChangeType.UPDATED);
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).initializeCollection("col-1");
            verify(lifecycleManager, never()).refreshCollection(any());
        }
    }

    @Nested
    @DisplayName("DELETED events")
    class DeletedEvents {

        @Test
        @DisplayName("Should tear down collection on DELETED event")
        void shouldTearDownCollection() throws Exception {
            String message = buildMessage("col-1", "orders", ChangeType.DELETED);
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).teardownCollection("col-1");
            verify(lifecycleManager, never()).initializeCollection(any());
            verify(lifecycleManager, never()).refreshCollection(any());
        }
    }

    @Nested
    @DisplayName("Message parsing")
    class MessageParsing {

        @Test
        @DisplayName("Should handle ConfigEvent wrapper format")
        void shouldHandleConfigEventWrapper() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of());

            String message = """
                {"eventType":"collection-changed","payload":{"id":"col-1","name":"tasks","changeType":"CREATED"}}
                """;
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).initializeCollection("col-1");
        }

        @Test
        @DisplayName("Should handle flat JSON format")
        void shouldHandleFlatJson() throws Exception {
            when(lifecycleManager.getActiveCollections()).thenReturn(Set.of());

            String message = """
                {"id":"col-1","name":"tasks","changeType":"CREATED"}
                """;
            listener.handleCollectionChanged(message);

            verify(lifecycleManager).initializeCollection("col-1");
        }

        @Test
        @DisplayName("Should handle unparseable message gracefully")
        void shouldHandleUnparseableMessage() {
            listener.handleCollectionChanged("not json at all");

            verify(lifecycleManager, never()).initializeCollection(any());
            verify(lifecycleManager, never()).refreshCollection(any());
            verify(lifecycleManager, never()).teardownCollection(any());
        }
    }

    private String buildMessage(String id, String name, ChangeType changeType) throws Exception {
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(id);
        payload.setName(name);
        payload.setChangeType(changeType);
        return objectMapper.writeValueAsString(payload);
    }
}
