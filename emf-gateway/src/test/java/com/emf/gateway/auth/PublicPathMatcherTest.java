package com.emf.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PublicPathMatcher}.
 */
class PublicPathMatcherTest {

    private static final List<String> BOOTSTRAP_PATHS = List.of(
            "/api/ui-pages", "/api/ui-menus", "/api/oidc-providers", "/api/tenants");

    @Test
    void shouldReturnTrueForGetOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ui-pages").build());

        assertThat(matcher.isPublicRequest(exchange)).isTrue();
    }

    @Test
    void shouldReturnTrueForHeadOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.head("/api/ui-menus").build());

        assertThat(matcher.isPublicRequest(exchange)).isTrue();
    }

    @Test
    void shouldReturnFalseForPostOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ui-pages").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldReturnFalseForPutOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/ui-pages/page-1").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldReturnFalseForPatchOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/tenants/tenant-1").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldReturnFalseForDeleteOnPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/oidc-providers/provider-1").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldReturnFalseForGetOnNonPublicPath() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNoPublicPathsConfigured() {
        PublicPathMatcher matcher = new PublicPathMatcher(Collections.emptyList());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ui-pages").build());

        assertThat(matcher.isPublicRequest(exchange)).isFalse();
    }

    @Test
    void shouldMatchByPrefix() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        // /api/ui-pages/some-id should match the /api/ui-pages prefix
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/ui-pages/some-page-id").build());

        assertThat(matcher.isPublicRequest(exchange)).isTrue();
    }

    @Test
    void shouldMatchAllConfiguredPaths() {
        PublicPathMatcher matcher = new PublicPathMatcher(BOOTSTRAP_PATHS);

        for (String path : BOOTSTRAP_PATHS) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build());
            assertThat(matcher.isPublicRequest(exchange))
                    .as("Expected %s to be public", path)
                    .isTrue();
        }
    }
}
