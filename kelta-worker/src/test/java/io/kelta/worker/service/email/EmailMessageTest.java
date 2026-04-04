package io.kelta.worker.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailMessage Tests")
class EmailMessageTest {

    @Test
    void shouldCreateEmailMessage() {
        var msg = new EmailMessage("user@example.com", "Welcome", "<h1>Hi</h1>", "Hi");
        assertEquals("user@example.com", msg.to());
        assertEquals("Welcome", msg.subject());
        assertEquals("<h1>Hi</h1>", msg.bodyHtml());
        assertEquals("Hi", msg.bodyText());
    }

    @Test
    void shouldSupportNullBodyText() {
        var msg = new EmailMessage("user@example.com", "Subject", "<p>HTML</p>", null);
        assertNull(msg.bodyText());
    }

    @Test
    void shouldImplementEquality() {
        var msg1 = new EmailMessage("a@b.com", "S", "<p>H</p>", "T");
        var msg2 = new EmailMessage("a@b.com", "S", "<p>H</p>", "T");
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentTo() {
        var msg1 = new EmailMessage("a@b.com", "S", "H", "T");
        var msg2 = new EmailMessage("c@d.com", "S", "H", "T");
        assertNotEquals(msg1, msg2);
    }
}
