package io.kelta.worker.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code Content-Disposition} value for an attachment download.
 *
 * <p>The filename in it is chosen by whoever sent the mail. It reaches a response header, so a
 * quote, a backslash or a newline in it is a header-injection primitive rather than a display
 * quirk. {@code MimeParser.sanitizeFilename} already strips control characters at ingest; this is
 * the second layer, and it is the one that has to hold if a row is ever written by any other path.
 */
@DisplayName("Attachment Content-Disposition")
class MailboxAttachmentDispositionTest {

    @Test
    @DisplayName("Always attachment, never inline")
    void alwaysAttachment() {
        // There is no inline branch to widen. That is deliberate.
        assertThat(MailboxController.contentDisposition("report.pdf")).startsWith("attachment;");
    }

    @Test
    @DisplayName("A quote in the filename cannot close the quoted string")
    void quotesCannotEscape() {
        String header = MailboxController.contentDisposition("in\"voice.pdf");

        // Exactly two quotes: the ones this method opened and closed itself.
        assertThat(header.chars().filter(c -> c == '"').count()).isEqualTo(2);
        assertThat(header).contains("in_voice.pdf");
    }

    @Test
    @DisplayName("CR and LF cannot start a header of the sender's choosing")
    void newlinesCannotInjectHeaders() {
        String header = MailboxController.contentDisposition(
                "x.pdf\r\nSet-Cookie: session=stolen");

        assertThat(header).doesNotContain("\r").doesNotContain("\n");
        // The bytes survive in the RFC 5987 form, percent-encoded, where they are inert.
        assertThat(header).contains("%0D%0A");
    }

    @Test
    @DisplayName("A backslash cannot escape the closing quote")
    void backslashIsNeutralised() {
        assertThat(MailboxController.contentDisposition("a\\\"b.txt"))
                .doesNotContain("\\");
    }

    @Test
    @DisplayName("Non-ASCII names survive in filename*, with an ASCII fallback alongside")
    void unicodeIsCarriedInTheEncodedForm() {
        String header = MailboxController.contentDisposition("рецепт.pdf");

        assertThat(header).contains("filename*=UTF-8''");
        // Cyrillic percent-encodes; the ASCII form degrades to underscores rather than mojibake.
        assertThat(header).contains("%D1%80");
        assertThat(header).contains("filename=\"");
    }

    @Test
    @DisplayName("An empty or missing filename still produces a valid header")
    void emptyFilenameFallsBack() {
        assertThat(MailboxController.contentDisposition(null)).contains("filename=\"attachment\"");
        assertThat(MailboxController.contentDisposition("   ")).contains("filename=\"attachment\"");
        // A name made entirely of characters the ASCII form strips must not yield filename="".
        assertThat(MailboxController.contentDisposition("\"\"\"")).contains("filename=\"___\"");
    }
}
