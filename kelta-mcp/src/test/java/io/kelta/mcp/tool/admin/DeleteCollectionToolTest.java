package io.kelta.mcp.tool.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class DeleteCollectionToolTest {

    private static final String COLLECTION_UUID = "11111111-2222-3333-4444-555555555555";

    private WireMockServer wm;
    private DeleteCollectionTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteCollectionTool(client);
        RequestPatHolder.set("klt_delete_coll");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollection() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_collection", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void deletesDirectlyWhenInputLooksLikeUuid() {
        wm.stubFor(delete(urlEqualTo("/api/collections/" + COLLECTION_UUID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_collection",
                        Map.of("collection", COLLECTION_UUID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("Deleted collection", COLLECTION_UUID, "204");
        wm.verify(WireMock.deleteRequestedFor(
                urlEqualTo("/api/collections/" + COLLECTION_UUID)));
        wm.verify(0, WireMock.getRequestedFor(urlPathEqualTo("/api/collections")));
    }

    @Test
    void resolvesNameToIdBeforeDeleting() {
        wm.stubFor(get(urlEqualTo(
                "/api/collections?filter[name][EQ]=e2e_wizard_1780349727631&page[size]=1"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"type\":\"collections\",\"id\":\"" + COLLECTION_UUID
                                + "\",\"attributes\":{\"name\":\"e2e_wizard_1780349727631\"}}]}")));
        wm.stubFor(delete(urlEqualTo("/api/collections/" + COLLECTION_UUID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_collection",
                        Map.of("collection", "e2e_wizard_1780349727631"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("e2e_wizard_1780349727631", COLLECTION_UUID, "204");
        wm.verify(WireMock.deleteRequestedFor(
                urlEqualTo("/api/collections/" + COLLECTION_UUID)));
    }

    @Test
    void reportsErrorWhenNameLookupReturnsEmpty() {
        wm.stubFor(get(urlEqualTo(
                "/api/collections?filter[name][EQ]=missing&page[size]=1"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_collection",
                        Map.of("collection", "missing"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("No collection found", "missing");
        wm.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching("/api/collections/.*")));
    }

    @Test
    void surfacesGatewayErrorOnDeleteFailure() {
        wm.stubFor(delete(urlEqualTo("/api/collections/" + COLLECTION_UUID))
                .willReturn(aResponse().withStatus(404).withBody(
                        "{\"errors\":[{\"detail\":\"not found\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_collection",
                        Map.of("collection", COLLECTION_UUID), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
