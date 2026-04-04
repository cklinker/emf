package io.kelta.worker.service.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PushDeliveryException Tests")
class PushDeliveryExceptionTest {

    @Test
    void shouldCreateWithInvalidTokenTrue() {
        var ex = new PushDeliveryException("Token expired", true);
        assertEquals("Token expired", ex.getMessage());
        assertTrue(ex.isInvalidToken());
    }

    @Test
    void shouldCreateWithInvalidTokenFalse() {
        var ex = new PushDeliveryException("Server error", false);
        assertEquals("Server error", ex.getMessage());
        assertFalse(ex.isInvalidToken());
    }

    @Test
    void shouldBeRuntimeException() {
        var ex = new PushDeliveryException("error", false);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
