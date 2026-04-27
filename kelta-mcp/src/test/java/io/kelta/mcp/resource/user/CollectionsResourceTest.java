package io.kelta.mcp.resource.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionsResourceTest {

    private WireMockServer wm;
    private CollectionsResource resource;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        resource = new CollectionsResource(client);
        RequestPatHolder.set("klt_res_collections");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void exposesKeltaCollectionsUri() {
        assertThat(resource.toSpecification().resource().uri()).isEqualTo("kelta://collections");
        assertThat(resource.toSpecification().resource().mimeType()).isEqualTo("application/json");
    }

    @Test
    void readsCollectionListFromGateway() {
        wm.stubFor(get(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[{\"id\":\"c1\",\"type\":\"collections\"}]}")));

        ReadResourceResult result = resource.toSpecification().readHandler().apply(
                null, new ReadResourceRequest("kelta://collections"));

        assertThat(result.contents()).hasSize(1);
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("kelta://collections");
        assertThat(contents.text()).contains("c1");
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/collections")));
    }

    @Test
    void wrapsGatewayErrorInJsonPayload() {
        wm.stubFor(get(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(503)));

        ReadResourceResult result = resource.toSpecification().readHandler().apply(
                null, new ReadResourceRequest("kelta://collections"));

        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.text()).contains("error", "503");
    }
}
