package io.kelta.worker.service.mailbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sniffing that V191 and {@code MailboxIngestService} both claimed to be doing before it
 * existed. Every case here is an attachment that lies about itself.
 */
@DisplayName("AttachmentContentType")
class AttachmentContentTypeTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("HTML wearing a .png content type is still HTML")
    void htmlIsDetectedRegardlessOfTheSendersClaim() {
        // The whole point: the sender's header never reaches this method.
        byte[] content = bytes("<!DOCTYPE html><html><body><script>alert(1)</script></body></html>");

        assertThat(AttachmentContentType.sniff(content)).isEqualTo("text/html");
        assertThat(AttachmentContentType.serveAs(AttachmentContentType.sniff(content)))
                .isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Leading whitespace does not hide markup")
    void leadingWhitespaceDoesNotHideHtml() {
        // A browser skips leading whitespace before deciding a document is HTML, so matching only
        // at byte zero would let "\n\n<html>" through as text/plain.
        assertThat(AttachmentContentType.sniff(bytes("\n\n   <html><body>hi</body></html>")))
                .isEqualTo("text/html");
    }

    @Test
    @DisplayName("A UTF-8 BOM does not hide markup either")
    void bomDoesNotHideHtml() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] html = bytes("<html><body>x</body></html>");
        byte[] content = new byte[bom.length + html.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(html, 0, content, bom.length, html.length);

        assertThat(AttachmentContentType.sniff(content)).isEqualTo("text/html");
    }

    @Test
    @DisplayName("SVG is markup, not an image")
    void svgIsTreatedAsActiveContent() {
        // SVG is the one image type that is also a scriptable document.
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";

        assertThat(AttachmentContentType.sniff(bytes(svg))).isEqualTo("image/svg+xml");
        assertThat(AttachmentContentType.isActive("image/svg+xml")).isTrue();
        assertThat(AttachmentContentType.serveAs("image/svg+xml"))
                .isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("SVG hidden behind an XML declaration is still SVG")
    void svgBehindAnXmlDeclaration() {
        assertThat(AttachmentContentType.sniff(bytes("<?xml version=\"1.0\"?>\n<svg><g/></svg>")))
                .isEqualTo("image/svg+xml");
    }

    @Test
    @DisplayName("Real binary formats are identified by signature")
    void identifiesCommonBinaryFormats() {
        assertThat(AttachmentContentType.sniff(new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo("image/png");
        assertThat(AttachmentContentType.sniff(new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).isEqualTo("image/jpeg");
        assertThat(AttachmentContentType.sniff(bytes("GIF89a"))).isEqualTo("image/gif");
        assertThat(AttachmentContentType.sniff(bytes("%PDF-1.7"))).isEqualTo("application/pdf");
        assertThat(AttachmentContentType.sniff(new byte[]{'P', 'K', 0x03, 0x04}))
                .isEqualTo("application/zip");
        assertThat(AttachmentContentType.sniff(new byte[]{'M', 'Z', 0x00, 0x00}))
                .isEqualTo("application/vnd.microsoft.portable-executable");
    }

    @Test
    @DisplayName("PDF is never served as itself")
    void pdfIsDownloadedNotRendered() {
        // Not markup, but browsers render it in a plugin with its own scripting engine, and
        // nothing in this feature needs it displayed inline.
        assertThat(AttachmentContentType.serveAs("application/pdf"))
                .isEqualTo("application/octet-stream");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/html", "application/xhtml+xml", "image/svg+xml", "text/xml", "application/xml",
            "application/javascript", "text/javascript", "text/css", "application/pdf"})
    @DisplayName("Every active type collapses to octet-stream when served")
    void activeTypesAreNeverServedAsThemselves(String type) {
        assertThat(AttachmentContentType.serveAs(type)).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Inert types are served as what they are")
    void inertTypesSurvive() {
        assertThat(AttachmentContentType.serveAs("image/png")).isEqualTo("image/png");
        assertThat(AttachmentContentType.serveAs("text/plain")).isEqualTo("text/plain");
        assertThat(AttachmentContentType.serveAs("application/zip")).isEqualTo("application/zip");
    }

    @Test
    @DisplayName("Unknown content is described as nothing rather than guessed at")
    void unknownContentIsOctetStream() {
        assertThat(AttachmentContentType.sniff(new byte[]{0x00, 0x01, 0x02, (byte) 0xFE}))
                .isEqualTo("application/octet-stream");
        assertThat(AttachmentContentType.sniff(null)).isEqualTo("application/octet-stream");
        assertThat(AttachmentContentType.sniff(new byte[0])).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Ordinary text is text")
    void plainTextIsPlainText() {
        assertThat(AttachmentContentType.sniff(bytes("Hi — could you check my booking?\nThanks")))
                .isEqualTo("text/plain");
    }

    @Test
    @DisplayName("A NUL byte means binary, whatever the rest looks like")
    void nulByteMeansBinary() {
        byte[] content = "plain looking text".getBytes(StandardCharsets.UTF_8).clone();
        content[4] = 0x00;

        assertThat(AttachmentContentType.sniff(content)).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Case and whitespace in a stored type do not smuggle an active type past serveAs")
    void serveAsNormalisesBeforeComparing() {
        assertThat(AttachmentContentType.serveAs("  TEXT/HTML  "))
                .isEqualTo("application/octet-stream");
        assertThat(AttachmentContentType.serveAs("Image/SVG+XML"))
                .isEqualTo("application/octet-stream");
        assertThat(AttachmentContentType.serveAs(null)).isEqualTo("application/octet-stream");
    }
}
