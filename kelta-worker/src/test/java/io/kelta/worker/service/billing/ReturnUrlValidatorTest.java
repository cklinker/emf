package io.kelta.worker.service.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These URLs are handed to the payment processor, which redirects a paying member
 * to them. Every rejection here is an open-redirect that would otherwise start
 * from inside a genuine payment flow.
 */
@DisplayName("ReturnUrlValidator Tests")
class ReturnUrlValidatorTest {

    private static final List<String> ALLOWED = List.of("https://app.example.com");

    private final ReturnUrlValidator validator = new ReturnUrlValidator();

    @Nested
    @DisplayName("Accepted")
    class Accepted {

        @Test
        @DisplayName("exact origin match")
        void exactOrigin() {
            assertThat(validator.isAllowed("https://app.example.com", ALLOWED)).isTrue();
        }

        @Test
        @DisplayName("any path, query, or fragment under an allowed origin")
        void pathsUnderAllowedOrigin() {
            assertThat(validator.isAllowed("https://app.example.com/billing/done", ALLOWED)).isTrue();
            assertThat(validator.isAllowed("https://app.example.com/done?x=1", ALLOWED)).isTrue();
            assertThat(validator.isAllowed("https://app.example.com/done#top", ALLOWED)).isTrue();
        }

        @Test
        @DisplayName("host comparison is case-insensitive")
        void hostCaseInsensitive() {
            assertThat(validator.isAllowed("https://APP.Example.COM/x", ALLOWED)).isTrue();
        }

        @Test
        @DisplayName("default port is equivalent to no port")
        void defaultPortEquivalence() {
            assertThat(validator.isAllowed("https://app.example.com:443/x", ALLOWED)).isTrue();
            assertThat(validator.isAllowed("https://app.example.com/x",
                    List.of("https://app.example.com:443"))).isTrue();
        }

        @Test
        @DisplayName("matches any entry in a multi-origin allowlist")
        void multipleAllowedOrigins() {
            List<String> allowed = List.of("https://a.example.com", "https://b.example.com");
            assertThat(validator.isAllowed("https://b.example.com/x", allowed)).isTrue();
        }

        @Test
        @DisplayName("http://localhost is allowed only when explicitly listed")
        void localhostWhenListed() {
            assertThat(validator.isAllowed("http://localhost:3000/done",
                    List.of("http://localhost:3000"))).isTrue();
            // …and not otherwise.
            assertThat(validator.isAllowed("http://localhost:3000/done", ALLOWED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Rejected")
    class Rejected {

        @Test
        @DisplayName("a different host")
        void differentHost() {
            assertThat(validator.isAllowed("https://evil.test/steal", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("a host that merely starts with the allowed one")
        void prefixLookalikeHost() {
            // The reason this validator compares origins instead of using
            // startsWith: both of these pass a naive prefix check.
            assertThat(validator.isAllowed("https://app.example.com.evil.test/x", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("https://app.example.company/x", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("a subdomain of an allowed host")
        void subdomainNotImplied() {
            assertThat(validator.isAllowed("https://evil.app.example.com/x", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("userinfo that disguises the real host")
        void userinfoDisguise() {
            // Reads as app.example.com to a human; resolves to evil.test.
            assertThat(validator.isAllowed("https://app.example.com@evil.test/x", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("https://app.example.com@evil.test/x",
                    List.of("https://evil.test"))).isFalse();
        }

        @Test
        @DisplayName("a non-default port that was not allowed")
        void nonDefaultPort() {
            assertThat(validator.isAllowed("https://app.example.com:8443/x", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("plain http on a non-localhost host")
        void plainHttp() {
            assertThat(validator.isAllowed("http://app.example.com/x", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("http://app.example.com/x",
                    List.of("http://app.example.com"))).isFalse();
        }

        @ParameterizedTest(name = "non-http scheme: {0}")
        @ValueSource(strings = {
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "file:///etc/passwd",
                "ftp://app.example.com/x"
        })
        @DisplayName("non-http(s) schemes")
        void nonHttpSchemes(String url) {
            assertThat(validator.isAllowed(url, ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("a relative or scheme-less URL")
        void relativeUrl() {
            assertThat(validator.isAllowed("/billing/done", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("app.example.com/done", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("//evil.test/x", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("null, blank, or malformed input")
        void nullBlankMalformed() {
            assertThat(validator.isAllowed(null, ALLOWED)).isFalse();
            assertThat(validator.isAllowed("", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("   ", ALLOWED)).isFalse();
            assertThat(validator.isAllowed("ht tp://app.example.com", ALLOWED)).isFalse();
        }

        @Test
        @DisplayName("an empty or null allowlist denies everything")
        void emptyAllowlistDeniesAll() {
            assertThat(validator.isAllowed("https://app.example.com", List.of())).isFalse();
            assertThat(validator.isAllowed("https://app.example.com", null)).isFalse();
        }

        @Test
        @DisplayName("a malformed allowlist entry never widens the allowlist")
        void malformedAllowlistEntryIgnored() {
            assertThat(validator.isAllowed("https://app.example.com",
                    List.of("not a url", "javascript:x"))).isFalse();
            // A valid entry alongside a broken one still works.
            assertThat(validator.isAllowed("https://app.example.com",
                    List.of("not a url", "https://app.example.com"))).isTrue();
        }
    }

    @Nested
    @DisplayName("Origin normalization")
    class OriginNormalization {

        @Test
        @DisplayName("strips path, query, and default port")
        void normalizes() {
            assertThat(validator.originOf("https://a.test/x?y=1#z")).isEqualTo("https://a.test");
            assertThat(validator.originOf("https://a.test:443/x")).isEqualTo("https://a.test");
            assertThat(validator.originOf("https://a.test:8443/x")).isEqualTo("https://a.test:8443");
            assertThat(validator.originOf("http://localhost:80/x")).isEqualTo("http://localhost");
        }

        @Test
        @DisplayName("returns null for anything unusable")
        void nullForUnusable() {
            assertThat(validator.originOf("/relative")).isNull();
            assertThat(validator.originOf("javascript:alert(1)")).isNull();
            assertThat(validator.originOf("https://user@a.test")).isNull();
            assertThat(validator.originOf("http://a.test")).isNull(); // non-localhost http
        }
    }
}
