package io.kelta.worker.listener;

import io.kelta.worker.service.SupersetDatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupersetCollectionSyncListener")
class SupersetCollectionSyncListenerTest {

    @Mock
    private SupersetDatasetService datasetService;

    private SupersetCollectionSyncListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new SupersetCollectionSyncListener(datasetService, objectMapper);
    }

    @Test
    @DisplayName("syncs dataset on CREATED event")
    void syncsOnCreated() {
        String message = """
                {
                    "changeType": "CREATED",
                    "tenantId": "t1",
                    "tenantSlug": "acme",
                    "collectionId": "col-1",
                    "collectionName": "accounts"
                }
                """;

        listener.onCollectionChanged(message);

        verify(datasetService).syncDatasetForCollection("t1", "acme", "col-1", "accounts");
    }

    @Test
    @DisplayName("syncs dataset on UPDATED event")
    void syncsOnUpdated() {
        String message = """
                {
                    "changeType": "UPDATED",
                    "tenantId": "t1",
                    "tenantSlug": "acme",
                    "collectionId": "col-1",
                    "collectionName": "accounts"
                }
                """;

        listener.onCollectionChanged(message);

        verify(datasetService).syncDatasetForCollection("t1", "acme", "col-1", "accounts");
    }

    @Test
    @DisplayName("ignores DELETED events")
    void ignoresDeletedEvents() {
        String message = """
                {
                    "changeType": "DELETED",
                    "tenantId": "t1",
                    "tenantSlug": "acme",
                    "collectionId": "col-1",
                    "collectionName": "accounts"
                }
                """;

        listener.onCollectionChanged(message);

        verify(datasetService, never()).syncDatasetForCollection(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("skips events with missing tenant info")
    void skipsIncompleteEvents() {
        String message = """
                {
                    "changeType": "CREATED",
                    "tenantId": "t1"
                }
                """;

        listener.onCollectionChanged(message);

        verify(datasetService, never()).syncDatasetForCollection(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handles malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.onCollectionChanged("not json");

        verify(datasetService, never()).syncDatasetForCollection(anyString(), anyString(), anyString(), anyString());
    }
}
