package io.kelta.worker.service.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmsMessage Tests")
class SmsMessageTest {

    @Test
    void shouldCreateSmsMessage() {
        var msg = new SmsMessage("+14155551234", "Your code is 1234");
        assertEquals("+14155551234", msg.to());
        assertEquals("Your code is 1234", msg.body());
    }

    @Test
    void shouldImplementEquality() {
        var msg1 = new SmsMessage("+1234", "hello");
        var msg2 = new SmsMessage("+1234", "hello");
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentTo() {
        var msg1 = new SmsMessage("+1234", "hello");
        var msg2 = new SmsMessage("+5678", "hello");
        assertNotEquals(msg1, msg2);
    }
}
