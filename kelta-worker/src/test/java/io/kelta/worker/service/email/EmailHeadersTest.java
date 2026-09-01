package io.kelta.worker.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailHeaders")
class EmailHeadersTest {

    @Test
    @DisplayName("none() is empty")
    void noneIsEmpty() {
        EmailHeaders none = EmailHeaders.none();
        assertThat(none.isEmpty()).isTrue();
        assertThat(none.extra()).isEmpty();
    }

    @Test
    @DisplayName("Blank values normalise to null so no empty header is emitted")
    void blankBecomesNull() {
        EmailHeaders h = new EmailHeaders("  ", "", "\t", null, null, null, Map.of());
        assertThat(h.replyTo()).isNull();
        assertThat(h.inReplyTo()).isNull();
        assertThat(h.references()).isNull();
        assertThat(h.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Surrounding whitespace is trimmed, not rejected")
    void valuesAreTrimmed() {
        EmailHeaders h = new EmailHeaders(null, "  <abc@example.com> ", null, null, null, null, Map.of());
        assertThat(h.inReplyTo()).isEqualTo("<abc@example.com>");
    }

    // ------------------------------------------------------------------
    // Header injection.
    //
    // In-Reply-To and References on a reply are copied from the inbound
    // message, so these values are attacker-controlled by default. A
    // smuggled CR or LF would terminate the header and let a sender append
    // further headers — or an entire body — to mail we send from our own
    // authenticated domain.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(strings = {
            "<a@b.com>\r\nBcc: victim@example.com",
            "<a@b.com>\nBcc: victim@example.com",
            "<a@b.com>\rX-Injected: yes",
            "<a@b.com>\r\n\r\nInjected body",
            "<a@b.com>\0",
    })
    @DisplayName("Control characters are rejected in every named value, not silently stripped")
    void rejectsHeaderInjection(String hostile) {
        assertThatThrownBy(() -> new EmailHeaders(hostile, null, null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reply-To");

        assertThatThrownBy(() -> new EmailHeaders(null, hostile, null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("In-Reply-To");

        assertThatThrownBy(() -> new EmailHeaders(null, null, hostile, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("References");

        assertThatThrownBy(() -> new EmailHeaders(null, null, null, hostile, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Auto-Submitted");
    }

    @Test
    @DisplayName("Injection through the extra map is rejected too")
    void rejectsInjectionInExtraValues() {
        Map<String, String> extra = Map.of("X-Custom", "ok\r\nBcc: victim@example.com");
        assertThatThrownBy(() -> new EmailHeaders(null, null, null, null, null, null, extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Custom");
    }

    @ParameterizedTest(name = "rejects header name [{0}]")
    @ValueSource(strings = {"X-Bad: Injected", "X Bad", "X-Bad\r\n", "Söme-Header"})
    @DisplayName("Header names outside printable US-ASCII, or containing a colon, are rejected")
    void rejectsIllegalHeaderNames(String name) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put(name, "value");
        assertThatThrownBy(() -> new EmailHeaders(null, null, null, null, null, null, extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Illegal header name");
    }

    @Test
    @DisplayName("Over-long values are rejected")
    void rejectsOverlongValues() {
        String huge = "<" + "a".repeat(5000) + "@example.com>";
        assertThatThrownBy(() -> new EmailHeaders(null, null, huge, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("A realistic reply header set is accepted")
    void acceptsRealisticReplyHeaders() {
        String chain = "<a@mail.example.com> <b@mail.example.com> <c@mail.example.com>";
        assertThatCode(() -> new EmailHeaders(
                "support+t123.ab12cd34@spotopened.com",
                "<c@mail.example.com>",
                chain,
                "auto-replied",
                null,
                "<mailto:unsub@example.com>",
                Map.of("X-Kelta-Thread", "t123")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("extra map is defensively copied — later mutation cannot alter the record")
    void extraIsDefensivelyCopied() {
        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("X-A", "1");
        EmailHeaders h = new EmailHeaders(null, null, null, null, null, null, mutable);
        mutable.put("X-B", "2");
        assertThat(h.extra()).containsExactly(Map.entry("X-A", "1"));
    }

    @Test
    @DisplayName("Blank-valued extra entries are dropped rather than emitted empty")
    void dropsBlankExtraValues() {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("X-Present", "yes");
        extra.put("X-Absent", "   ");
        EmailHeaders h = new EmailHeaders(null, null, null, null, null, null, extra);
        assertThat(h.extra()).containsExactly(Map.entry("X-Present", "yes"));
    }

    @Test
    @DisplayName("Null extra map is tolerated")
    void nullExtraIsTolerated() {
        EmailHeaders h = new EmailHeaders(null, null, null, "auto-replied", null, null, null);
        assertThat(h.extra()).isEmpty();
        assertThat(h.isEmpty()).isFalse();
    }
}
