package io.kelta.worker.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailDeliveryException Tests")
class EmailDeliveryExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        var ex = new EmailDeliveryException("SMTP error");
        assertEquals("SMTP error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        var cause = new RuntimeException("connection refused");
        var ex = new EmailDeliveryException("SMTP error", cause);
        assertEquals("SMTP error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void shouldBeRuntimeException() {
        assertInstanceOf(RuntimeException.class, new EmailDeliveryException("error"));
    }
}
