package io.kelta.worker.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.svix.Svix;
import com.svix.api.Message;
import com.svix.models.MessageIn;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("SvixWebhookPublisher")
class SvixWebhookPublisherTest {

    private Svix svix;
    private Message messageApi;
    private ObjectMapper objectMapper;
    private SvixWebhookPublisher publisher;

    @BeforeEach
    void setUp() {
        svix = mock(Svix.class);
        messageApi = mock(Message.class);
        when(svix.getMessage()).thenReturn(messageApi);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisher = new SvixWebhookPublisher(svix, objectMapper);
    }

    private String buildEventJson(String tenantId, String collectionName, ChangeType changeType) throws Exception {
        var payload = new CollectionChangedPayload();
        payload.setId(UUID.randomUUID().toString());
        payload.setName(collectionName);
        payload.setDisplayName(collectionName);
        payload.setChangeType(changeType);

        var event = new PlatformEvent<CollectionChangedPayload>();
        event.setEventId(UUID.randomUUID().toString());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setPayload(payload);

        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("publishes collection name as channel on the message")
    void publishesCollectionNameAsChannel() throws Exception {
        String json = buildEventJson("tenant-1", "accounts", ChangeType.CREATED);

        publisher.onCollectionChanged(json);

        ArgumentCaptor<MessageIn> captor = ArgumentCaptor.forClass(MessageIn.class);
        verify(messageApi).create(eq("tenant-1"), captor.capture());

        MessageIn sent = captor.getValue();
        assertEquals("collection.created", sent.getEventType());
        assertNotNull(sent.getChannels());
        assertTrue(sent.getChannels().contains("accounts"));
    }

    @Test
    @DisplayName("publishes updated event with collection channel")
    void publishesUpdatedEventWithChannel() throws Exception {
        String json = buildEventJson("tenant-2", "contacts", ChangeType.UPDATED);

        publisher.onCollectionChanged(json);

        ArgumentCaptor<MessageIn> captor = ArgumentCaptor.forClass(MessageIn.class);
        verify(messageApi).create(eq("tenant-2"), captor.capture());

        MessageIn sent = captor.getValue();
        assertEquals("collection.updated", sent.getEventType());
        assertEquals(Set.of("contacts"), sent.getChannels());
    }

    @Test
    @DisplayName("does not publish when tenant ID is missing")
    void skipsMissingTenantId() throws Exception {
        String json = buildEventJson(null, "accounts", ChangeType.CREATED);

        publisher.onCollectionChanged(json);

        verify(messageApi, never()).create(anyString(), any(MessageIn.class));
    }

    @Test
    @DisplayName("does not publish for unhandled change type")
    void skipsUnhandledChangeType() throws Exception {
        String json = buildEventJson("tenant-1", "accounts", ChangeType.DELETED);

        publisher.onCollectionChanged(json);

        verify(messageApi, never()).create(anyString(), any(MessageIn.class));
    }

    @Test
    @DisplayName("payload contains collection metadata")
    void payloadContainsCollectionMetadata() throws Exception {
        String json = buildEventJson("tenant-1", "orders", ChangeType.CREATED);

        publisher.onCollectionChanged(json);

        ArgumentCaptor<MessageIn> captor = ArgumentCaptor.forClass(MessageIn.class);
        verify(messageApi).create(eq("tenant-1"), captor.capture());

        String payloadJson = captor.getValue().getPayload();
        var payloadMap = objectMapper.readValue(payloadJson, java.util.Map.class);
        assertEquals("orders", payloadMap.get("collectionName"));
        assertEquals("orders", payloadMap.get("displayName"));
        assertEquals("CREATED", payloadMap.get("changeType"));
    }
}
