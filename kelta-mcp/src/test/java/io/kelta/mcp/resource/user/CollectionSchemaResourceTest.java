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

class CollectionSchemaResourceTest {

    private WireMockServer wm;
    private CollectionSchemaResource resource;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000));
        resource = new CollectionSchemaResource(client);
        RequestPatHolder.set("klt_res_schema");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void exposesTemplateUri() {
        assertThat(resource.toSpecification().resourceTemplate().uriTemplate())
                .isEqualTo("kelta://collections/{name}");
    }

    @Test
    void resolvesNameFromUriAndCombinesCollectionAndFields() {
        wm.stubFor(get(urlEqualTo("/api/collections/accounts"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"id\":\"c1\",\"attributes\":{\"name\":\"accounts\"}}}")));
        wm.stubFor(get(urlEqualTo("/api/fields?filter[collectionName][EQ]=accounts&page[size]=200"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[{\"id\":\"f1\"}]}")));

        ReadResourceResult result = resource.toSpecification().readHandler().apply(
                null, new ReadResourceRequest("kelta://collections/accounts"));

        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("kelta://collections/accounts");
        assertThat(contents.text()).contains("\"collection\":", "\"fields\":", "c1", "f1");
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/collections/accounts")));
        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/fields?filter[collectionName][EQ]=accounts&page[size]=200")));
    }

    @Test
    void returnsErrorPayloadForBadUri() {
        ReadResourceResult result = resource.toSpecification().readHandler().apply(
                null, new ReadResourceRequest("kelta://something-else"));

        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.text()).contains("error", "unrecognized");
    }

    @Test
    void wrapsCollectionLookupErrorInJsonPayload() {
        wm.stubFor(get(urlEqualTo("/api/collections/missing"))
                .willReturn(aResponse().withStatus(404)));

        ReadResourceResult result = resource.toSpecification().readHandler().apply(
                null, new ReadResourceRequest("kelta://collections/missing"));

        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.text()).contains("error", "404");
    }
}
