package io.kelta.worker.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PushMessage Tests")
class PushMessageTest {

    @Test
    void shouldCreatePushMessage() {
        var msg = new PushMessage("token-abc", "ios", "Hello", "World", Map.of("key", "val"));

        assertEquals("token-abc", msg.deviceToken());
        assertEquals("ios", msg.platform());
        assertEquals("Hello", msg.title());
        assertEquals("World", msg.body());
        assertEquals(Map.of("key", "val"), msg.data());
    }

    @Test
    void shouldSupportNullData() {
        var msg = new PushMessage("token", "android", "Title", "Body", null);
        assertNull(msg.data());
    }

    @Test
    void shouldSupportEmptyData() {
        var msg = new PushMessage("token", "web", "T", "B", Map.of());
        assertTrue(msg.data().isEmpty());
    }

    @Test
    void shouldImplementEquality() {
        var msg1 = new PushMessage("t", "ios", "T", "B", null);
        var msg2 = new PushMessage("t", "ios", "T", "B", null);
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentToken() {
        var msg1 = new PushMessage("t1", "ios", "T", "B", null);
        var msg2 = new PushMessage("t2", "ios", "T", "B", null);
        assertNotEquals(msg1, msg2);
    }
}
