package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.PlatformEvent;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NatsEventPublisher")
class NatsEventPublisherTest {

    @Mock
    private NatsConnectionManager connectionManager;

    @Mock
    private JetStream jetStream;

    private ObjectMapper objectMapper;

    private PlatformEvent<String> event() {
        return new PlatformEvent<>("evt-1", "test.event", "tenant-1",
                "corr-1", "user-1", Instant.now(), "payload");
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("successful publish increments success counter and releases permit")
    void successfulPublishReleasesPermit() throws Exception {
        when(connectionManager.jetStream()).thenReturn(jetStream);
        PublishAck ack = ackStub();
        when(jetStream.publishAsync(any(NatsMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(ack));
        NatsEventPublisher publisher = new NatsEventPublisher(
                connectionManager, objectMapper, 10, null);

        publisher.publish("subject", event());

        await(() -> publisher.successCount() == 1L);
        assertThat(publisher.successCount()).isEqualTo(1L);
        assertThat(publisher.failureCount()).isEqualTo(0L);
        assertThat(publisher.droppedCount()).isEqualTo(0L);
        assertThat(publisher.availableInflightPermits()).isEqualTo(10);
    }

    @Test
    @DisplayName("failed publish future increments failure counter and releases permit")
    void failedPublishReleasesPermit() throws Exception {
        when(connectionManager.jetStream()).thenReturn(jetStream);
        CompletableFuture<PublishAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(jetStream.publishAsync(any(NatsMessage.class))).thenReturn(failed);
        NatsEventPublisher publisher = new NatsEventPublisher(
                connectionManager, objectMapper, 10, null);

        publisher.publish("subject", event());

        await(() -> publisher.failureCount() == 1L);
        assertThat(publisher.failureCount()).isEqualTo(1L);
        assertThat(publisher.availableInflightPermits()).isEqualTo(10);
    }

    @Test
    @DisplayName("inflight cap drops publish and increments dropped counter")
    void inflightCapDropsExcessPublishes() throws Exception {
        when(connectionManager.jetStream()).thenReturn(jetStream);
        CompletableFuture<PublishAck> neverCompletes = new CompletableFuture<>();
        when(jetStream.publishAsync(any(NatsMessage.class))).thenReturn(neverCompletes);
        NatsEventPublisher publisher = new NatsEventPublisher(
                connectionManager, objectMapper, 1, null);

        publisher.publish("subject", event());
        publisher.publish("subject", event());

        assertThat(publisher.droppedCount()).isEqualTo(1L);
        assertThat(publisher.successCount()).isEqualTo(0L);
        assertThat(publisher.availableInflightPermits()).isEqualTo(0);
    }

    @Test
    @DisplayName("invokes NatsTracing.inject so trace context propagates to subscribers")
    void invokesTracingInject() throws Exception {
        when(connectionManager.jetStream()).thenReturn(jetStream);
        PublishAck ack = ackStub();
        when(jetStream.publishAsync(any(NatsMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(ack));
        NatsTracing tracing = mock(NatsTracing.class);
        NatsEventPublisher publisher = new NatsEventPublisher(
                connectionManager, objectMapper, 10, null, tracing);

        publisher.publish("subject", event());

        ArgumentCaptor<Headers> captor = ArgumentCaptor.forClass(Headers.class);
        verify(tracing).inject(captor.capture());
        assertThat(captor.getValue().getFirst("Nats-Msg-Id")).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("synchronous publish failure releases permit and increments failure counter")
    void synchronousFailureReleasesPermit() throws Exception {
        when(connectionManager.jetStream()).thenReturn(jetStream);
        when(jetStream.publishAsync(any(NatsMessage.class)))
                .thenThrow(new RuntimeException("connection lost"));
        NatsEventPublisher publisher = new NatsEventPublisher(
                connectionManager, objectMapper, 5, null);

        publisher.publish("subject", event());

        assertThat(publisher.failureCount()).isEqualTo(1L);
        assertThat(publisher.availableInflightPermits()).isEqualTo(5);
    }

    private static PublishAck ackStub() {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getSeqno()).thenReturn(1L);
        when(ack.getStream()).thenReturn("TEST");
        return ack;
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
