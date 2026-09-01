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

    @Test
    @DisplayName("Credentials in the authority reject the URL, however allowed the host looks")
    void rejectsUserInfo() {
        // https://paypal.com@attacker.test/ reads as PayPal at a glance. The host here IS allowed,
        // so the check cannot lean on host comparison alone -- any userinfo has to reject outright.
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://paypal.com@app.example.test/ok", List.of("https://app.example.test")))
                .isFalse();
    }

    @Test
    @DisplayName("Plain HTTP is refused even when the tenant configured an http:// origin")
    void refusesPlainHttpOffLoopback() {
        // This is where a member lands after paying. A tenant must not be able to downgrade it.
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "http://app.example.test/ok", List.of("http://app.example.test")))
                .isFalse();
    }

    @Test
    @DisplayName("Loopback stays on HTTP so local development still works")
    void allowsHttpOnLoopback() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "http://localhost:5173/ok", List.of("http://localhost:5173")))
                .isTrue();
    }

    @Test
    @DisplayName("A default port matches its implicit form, in both directions")
    void normalisesDefaultPorts() {
        // Raw port comparison made a correctly configured origin reject its own URL: URI reports
        // -1 for the implicit form and 443 for the explicit one.
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.test:443/ok", List.of("https://app.example.test")))
                .isTrue();
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.test/ok", List.of("https://app.example.test:443")))
                .isTrue();
    }

    @Test
    @DisplayName("A non-default port still has to match")
    void nonDefaultPortMustMatch() {
        assertThat(CreateCheckoutSessionActionHandler.isAllowedUrl(
                "https://app.example.test:8443/ok", List.of("https://app.example.test")))
                .isFalse();
    }
}
