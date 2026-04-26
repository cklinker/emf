package io.kelta.worker.listener;

import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("SvixWebhookPublisher")
class SvixWebhookPublisherTest {

    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private SvixWebhookPublisher publisher;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder()
                .baseUrl("http://svix.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = new ObjectMapper();
        publisher = new SvixWebhookPublisher(builder.build(), objectMapper);
    }

    private String buildEventJson(String tenantId, String collectionName, ChangeType changeType) {
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
    void publishesCollectionNameAsChannel() {
        String json = buildEventJson("tenant-1", "accounts", ChangeType.CREATED);

        server.expect(requestTo("http://svix.test/api/v1/app/tenant-1/msg/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(jsonPath("$.eventType").value("collection.created"))
                .andExpect(jsonPath("$.channels[0]").value("accounts"))
                .andExpect(jsonPath("$.payload.collectionName").value("accounts"))
                .andRespond(withSuccess());

        publisher.onCollectionChanged(json);

        server.verify();
    }

    @Test
    @DisplayName("publishes updated event with collection channel")
    void publishesUpdatedEventWithChannel() {
        String json = buildEventJson("tenant-2", "contacts", ChangeType.UPDATED);

        server.expect(requestTo("http://svix.test/api/v1/app/tenant-2/msg/"))
                .andExpect(jsonPath("$.eventType").value("collection.updated"))
                .andExpect(jsonPath("$.channels[0]").value("contacts"))
                .andRespond(withSuccess());

        publisher.onCollectionChanged(json);

        server.verify();
    }

    @Test
    @DisplayName("does not publish when tenant ID is missing")
    void skipsMissingTenantId() {
        String json = buildEventJson(null, "accounts", ChangeType.CREATED);

        publisher.onCollectionChanged(json);

        server.verify();
    }

    @Test
    @DisplayName("does not publish for unhandled change type")
    void skipsUnhandledChangeType() {
        String json = buildEventJson("tenant-1", "accounts", ChangeType.DELETED);

        publisher.onCollectionChanged(json);

        server.verify();
    }

    @Test
    @DisplayName("payload contains collection metadata")
    void payloadContainsCollectionMetadata() {
        String json = buildEventJson("tenant-1", "orders", ChangeType.CREATED);

        server.expect(requestTo("http://svix.test/api/v1/app/tenant-1/msg/"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.payload.collectionName").value("orders"))
                .andExpect(jsonPath("$.payload.displayName").value("orders"))
                .andExpect(jsonPath("$.payload.changeType").value("CREATED"))
                .andRespond(withSuccess());

        publisher.onCollectionChanged(json);

        server.verify();
    }

    @Test
    @DisplayName("swallows Svix errors and does not throw")
    void swallowsErrors() {
        String json = buildEventJson("tenant-1", "accounts", ChangeType.CREATED);

        server.expect(requestTo("http://svix.test/api/v1/app/tenant-1/msg/"))
                .andRespond(withServerError());

        publisher.onCollectionChanged(json);

        server.verify();
    }
}
