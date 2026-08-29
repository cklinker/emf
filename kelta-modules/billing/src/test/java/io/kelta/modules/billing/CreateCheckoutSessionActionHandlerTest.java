package io.kelta.modules.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Return-URL validation is the security-bearing part of checkout: without it a caller could send
 * a paying member to a page they control after payment.
 */
@DisplayName("CreateCheckoutSessionActionHandler — return URL validation")
class CreateCheckoutSessionActionHandlerTest {

    private static final List<String> ALLOWED = List.of("https://app.example.com");

    @Test
    @DisplayName("Accepts a URL on an allowed origin")
    void acceptsAllowedOrigin() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.com/billing/done?x=1", ALLOWED)).isTrue();
    }

    @Test
    @DisplayName("Rejects a host that merely starts with an allowed one")
    void rejectsSuffixAttack() {
        // The classic prefix-matching bug: good.example.com must not authorize
        // good.example.com.evil.test.
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.com.evil.test/steal", ALLOWED)).isFalse();
    }

    @Test
    @DisplayName("Rejects a different scheme, host, or port")
    void rejectsOriginMismatch() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "http://app.example.com/done", ALLOWED)).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://other.example.com/done", ALLOWED)).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.com:8443/done", ALLOWED)).isFalse();
    }

    @Test
    @DisplayName("Host comparison is case-insensitive")
    void hostIsCaseInsensitive() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://APP.Example.COM/done", ALLOWED)).isTrue();
    }

    @Test
    @DisplayName("Fails closed on empty, blank, or unparseable input")
    void failsClosed() {
        // No configured origins must authorize nothing, rather than everything.
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.com/done", List.of())).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(null, ALLOWED)).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl("   ", ALLOWED)).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "not a url", ALLOWED)).isFalse();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "/relative/path", ALLOWED)).isFalse();
    }

    @Test
    @DisplayName("A malformed configured origin authorizes nothing rather than throwing")
    void malformedConfiguredOriginIsIgnored() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.com/done", List.of(":::not-a-url"))).isFalse();
    }
}
