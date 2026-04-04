package io.kelta.worker.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogOnlyPushProvider Tests")
class LogOnlyPushProviderTest {

    @Test
    void shouldSendWithoutException() {
        var provider = new LogOnlyPushProvider();
        var message = new PushMessage("token-123", "ios", "Hello", "World", Map.of("action", "open"));

        assertDoesNotThrow(() -> provider.send(message));
    }

    @Test
    void shouldImplementPushProvider() {
        assertInstanceOf(PushProvider.class, new LogOnlyPushProvider());
    }

    @Test
    void shouldHandleNullDataPayload() {
        var provider = new LogOnlyPushProvider();
        var message = new PushMessage("token", "android", "Title", "Body", null);
        assertDoesNotThrow(() -> provider.send(message));
    }
}
