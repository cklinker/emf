package io.kelta.mcp.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.config.McpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayHttpClientTest {

    private WireMockServer wm;
    private GatewayHttpClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        McpProperties props = new McpProperties("http://localhost:" + wm.port(), 30, 60_000);
        client = new GatewayHttpClient(RestClient.builder(), props);
        RequestPatHolder.set("klt_test_pat_value");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void getForwardsBearerTokenInAuthorizationHeader() {
        wm.stubFor(get(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        GatewayHttpClient.Response res = client.get("/api/collections");

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.body()).isEqualTo("{\"data\":[]}");
        wm.verify(getRequestedForUrl("/api/collections")
                .withHeader("Authorization", equalTo("Bearer klt_test_pat_value"))
                .withHeader("X-Tenant-ID", absent()));
    }

    @Test
    void postSendsJsonBody() {
        wm.stubFor(post(urlEqualTo("/api/accounts"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"a1\"}}")));

        GatewayHttpClient.Response res = client.post("/api/accounts",
                java.util.Map.of("data", java.util.Map.of("type", "accounts",
                        "attributes", java.util.Map.of("name", "Acme"))));

        assertThat(res.isSuccess()).isTrue();
        wm.verify(postRequestedForUrl("/api/accounts")
                .withHeader("Authorization", equalTo("Bearer klt_test_pat_value"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.data.attributes.name")));
    }

    @Test
    void patchSendsJsonBody() {
        wm.stubFor(patch(urlEqualTo("/api/accounts/a1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":{}}")));

        GatewayHttpClient.Response res = client.patch("/api/accounts/a1",
                java.util.Map.of("data", java.util.Map.of("attributes", java.util.Map.of("name", "Acme2"))));

        assertThat(res.isSuccess()).isTrue();
        wm.verify(patchRequestedForUrl("/api/accounts/a1")
                .withHeader("Authorization", equalTo("Bearer klt_test_pat_value")));
    }

    @Test
    void deleteUsesNoBody() {
        wm.stubFor(delete(urlEqualTo("/api/accounts/a1"))
                .willReturn(aResponse().withStatus(204)));

        GatewayHttpClient.Response res = client.delete("/api/accounts/a1");

        assertThat(res.isSuccess()).isTrue();
        wm.verify(deleteRequestedForUrl("/api/accounts/a1")
                .withHeader("Authorization", equalTo("Bearer klt_test_pat_value")));
    }

    @Test
    void surfaceErrorBodyOnNon2xx() {
        wm.stubFor(get(urlEqualTo("/api/collections/missing"))
                .willReturn(aResponse().withStatus(404).withBody("{\"errors\":[{\"detail\":\"not found\"}]}")));

        GatewayHttpClient.Response res = client.get("/api/collections/missing");

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.status().value()).isEqualTo(404);
        assertThat(res.body()).contains("not found");
    }

    @Test
    void rejectsCallWhenNoPatInContext() {
        RequestPatHolder.clear();

        assertThatThrownBy(() -> client.get("/api/collections"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No PAT in request context");

        wm.verify(0, getRequestedForUrl("/api/collections"));
    }

    private static RequestPatternBuilder getRequestedForUrl(String url) {
        return WireMock.getRequestedFor(urlEqualTo(url));
    }

    private static RequestPatternBuilder postRequestedForUrl(String url) {
        return WireMock.postRequestedFor(urlEqualTo(url));
    }

    private static RequestPatternBuilder patchRequestedForUrl(String url) {
        return WireMock.patchRequestedFor(urlEqualTo(url));
    }

    private static RequestPatternBuilder deleteRequestedForUrl(String url) {
        return WireMock.deleteRequestedFor(urlEqualTo(url));
    }
}
