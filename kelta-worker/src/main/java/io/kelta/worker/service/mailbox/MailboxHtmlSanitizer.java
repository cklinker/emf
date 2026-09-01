package io.kelta.worker.service.mailbox;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Sanitises inbound email HTML.
 *
 * <p>This is the first code in the platform to render untrusted third-party HTML, so the policy
 * is written out rather than inherited. It runs <b>once at ingest</b>, not on read: sanitising on
 * read makes every reader pay the cost and, worse, makes any future policy bug immediately
 * exploitable against every message ever received. A policy change is a backfill over the stored
 * raw MIME instead.
 *
 * <p>Server sanitisation is only half the defence. The result is still rendered inside a
 * {@code sandbox=""} iframe with a restrictive CSP, because a sanitiser is a denylist-shaped
 * problem no matter how it is written, and the browser boundary does not depend on getting the
 * parser right.
 *
 * @since 1.0.0
 */
@Component
public class MailboxHtmlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(MailboxHtmlSanitizer.class);

    /** Beyond this, refuse to parse. Parser DoS is cheap to attempt and cheap to prevent. */
    private static final int MAX_INPUT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_NODES = 50_000;

    /** Marks an image whose remote source was withheld pending an explicit click-to-load. */
    public static final String BLOCKED_ATTR = "data-kelta-blocked";
    public static final String REMOTE_SRC_ATTR = "data-kelta-remote-src";
    public static final String CID_ATTR = "data-kelta-cid";

    private final Safelist safelist = buildSafelist();

    /**
     * Built up from {@link Safelist#none()} rather than trimmed down from
     * {@link Safelist#relaxed()}. Relaxed permits {@code style} on several elements, and a
     * subtractive policy silently regains anything a future jsoup release adds to its baseline.
     */
    private static Safelist buildSafelist() {
        return Safelist.none()
                .addTags("p", "br", "div", "span", "a", "b", "strong", "i", "em", "u", "s",
                        "blockquote", "pre", "code", "ul", "ol", "li", "dl", "dt", "dd",
                        "h1", "h2", "h3", "h4", "h5", "h6", "hr",
                        "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "img")
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("th", "colspan", "rowspan", "scope")
                .addProtocols("a", "href", "http", "https", "mailto")
                // http(s) is allowed HERE only so the attribute survives the Cleaner and reaches
                // the post-pass below, which removes it and parks the URL in a data- attribute.
                // Omitting it makes the Cleaner delete the URL outright, which looks safer and is
                // actually worse: the agent then has no way to opt into loading the image, and a
                // legitimate message renders as a row of empty boxes with the originals lost.
                // No http(s) src reaches the serialised output either way.
                .addProtocols("img", "src", "cid", "data", "http", "https");
        // Never added, and worth naming so nobody re-adds them: script, style, link, meta, base,
        // iframe, object, embed, form, input, button, select, textarea, svg, math, template,
        // noscript; the style attribute; class; id; srcset; formaction; xlink:href.
    }

    /** The sanitised markup plus what had to be removed, for the UI to explain. */
    public record Result(String html, int blockedImages, boolean truncated) {
        public static Result empty() {
            return new Result(null, 0, false);
        }
    }

    public Result sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return Result.empty();
        }
        if (rawHtml.length() > MAX_INPUT_BYTES) {
            log.warn("Refusing to sanitise {} chars of HTML, over the {} limit",
                    rawHtml.length(), MAX_INPUT_BYTES);
            return new Result(null, 0, true);
        }

        try {
            Document dirty = Jsoup.parse(rawHtml);
            if (countNodes(dirty) > MAX_NODES) {
                log.warn("Refusing to sanitise a document with over {} nodes", MAX_NODES);
                return new Result(null, 0, true);
            }

            Document clean = new Cleaner(safelist).clean(dirty);

            // Comments are dropped explicitly. The Cleaner happens to discard them today, but
            // conditional comments have historically been a script-execution vector and that is
            // not a property to depend on incidentally.
            removeComments(clean);

            int blocked = 0;
            for (Element img : clean.select("img[src]")) {
                String src = img.attr("src").trim();
                String scheme = schemeOf(src);
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // Park the URL instead of loading it. A remote image is a read receipt: it
                    // tells the sender the exact moment a support agent opened the message, and
                    // leaks the agent's IP. The UI offers an explicit "load images".
                    img.removeAttr("src");
                    img.attr(REMOTE_SRC_ATTR, src);
                    img.attr(BLOCKED_ATTR, "1");
                    blocked++;
                } else if ("cid".equals(scheme)) {
                    img.removeAttr("src");
                    img.attr(CID_ATTR, src.substring("cid:".length()));
                }
            }

            for (Element a : clean.select("a[href]")) {
                // Re-check after unescaping: the Cleaner validated the raw attribute, and a
                // scheme can hide behind entity encoding.
                if (!isSafeLink(a.attr("href"))) {
                    a.removeAttr("href");
                    continue;
                }
                a.attr("rel", "noopener noreferrer nofollow");
                a.attr("target", "_blank");
            }

            clean.outputSettings()
                    .escapeMode(Entities.EscapeMode.base)
                    .prettyPrint(false);

            return new Result(clean.body().html(), blocked, false);

        } catch (Exception e) {
            // A message whose HTML cannot be sanitised is still a real customer message: keep the
            // text body and show nothing rather than dropping the message.
            log.warn("Failed to sanitise inbound HTML, dropping the HTML body: {}", e.getMessage());
            return Result.empty();
        }
    }

    private static boolean isSafeLink(String href) {
        String scheme = schemeOf(href);
        return "http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme);
    }

    /** Null for a relative or schemeless URL, which is not something we keep. */
    private static String schemeOf(String url) {
        if (url == null) {
            return null;
        }
        String v = org.jsoup.parser.Parser.unescapeEntities(url, true).trim()
                .replaceAll("[\\p{Cntrl}\\s]", "");
        int colon = v.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return v.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    private static void removeComments(Document doc) {
        doc.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                // no-op
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Comment) {
                    node.remove();
                }
            }
        });
    }

    private static int countNodes(Document doc) {
        int[] count = {0};
        doc.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                count[0]++;
            }

            @Override
            public void tail(Node node, int depth) {
                // no-op
            }
        });
        return count[0];
    }
}
