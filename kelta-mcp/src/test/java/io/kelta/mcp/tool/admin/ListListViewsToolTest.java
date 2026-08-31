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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ListListViewsToolTest {

    private static final String COLLECTION_ID = "11111111-1111-1111-1111-111111111111";

    private WireMockServer wm;
    private ListListViewsTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new ListListViewsTool(client);
        RequestPatHolder.set("klt_list_listviews");
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=orders"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"type\":\"collections\",\"id\":\"" + COLLECTION_ID + "\"}]}")));
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_listviews", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void listsViewsByCollectionName() {
        wm.stubFor(get(urlEqualTo(
                "/api/list-views?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=200"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[{\"id\":\"lv1\",\"type\":\"list-views\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_listviews",
                        Map.of("collectionName", "orders"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"id\":\"lv1\"");
        wm.verify(WireMock.getRequestedFor(urlEqualTo(
                "/api/list-views?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=200")));
    }

    @Test
    void acceptsCollectionUuidDirectly() {
        wm.stubFor(get(urlEqualTo(
                "/api/list-views?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=200"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_listviews",
                        Map.of("collectionName", COLLECTION_ID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(0, WireMock.getRequestedFor(urlEqualTo("/api/collections?filter[name][eq]=" + COLLECTION_ID)));
    }

    @Test
    void reportsErrorWhenCollectionNotFound() {
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=nope"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_listviews",
                        Map.of("collectionName", "nope"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("nope", "not found");
    }

    @Test
    void appliesPageSize() {
        wm.stubFor(get(urlEqualTo(
                "/api/list-views?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=10"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_listviews",
                        Map.of("collectionName", "orders", "pageSize", 10), null));

        wm.verify(WireMock.getRequestedFor(urlEqualTo(
                "/api/list-views?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=10")));
    }
}
