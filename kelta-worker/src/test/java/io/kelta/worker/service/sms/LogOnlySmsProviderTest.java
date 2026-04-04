package io.kelta.worker.service.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogOnlySmsProvider Tests")
class LogOnlySmsProviderTest {

    @Test
    void shouldSendWithoutException() {
        var provider = new LogOnlySmsProvider();
        var message = new SmsMessage("+14155551234", "Your verification code is 1234");

        assertDoesNotThrow(() -> provider.send(message));
    }

    @Test
    void shouldImplementSmsProvider() {
        assertInstanceOf(SmsProvider.class, new LogOnlySmsProvider());
    }

    @Test
    void shouldHandleEmptyBody() {
        var provider = new LogOnlySmsProvider();
        var message = new SmsMessage("+1234", "");
        assertDoesNotThrow(() -> provider.send(message));
    }
}
