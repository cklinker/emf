package io.kelta.worker.service.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmsDeliveryException Tests")
class SmsDeliveryExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        var ex = new SmsDeliveryException("Delivery failed");
        assertEquals("Delivery failed", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        var cause = new RuntimeException("network error");
        var ex = new SmsDeliveryException("Delivery failed", cause);
        assertEquals("Delivery failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void shouldBeRuntimeException() {
        assertInstanceOf(RuntimeException.class, new SmsDeliveryException("error"));
    }
}
