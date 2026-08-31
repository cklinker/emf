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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class DeleteListViewToolTest {

    private static final String LV_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private WireMockServer wm;
    private DeleteListViewTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteListViewTool(client);
        RequestPatHolder.set("klt_delete_listview");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_listview", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void deletesSuccessfully() {
        wm.stubFor(delete(urlEqualTo("/api/list-views/" + LV_UUID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_listview",
                        Map.of("id", LV_UUID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("Deleted list view", LV_UUID, "204");
        wm.verify(WireMock.deleteRequestedFor(urlEqualTo("/api/list-views/" + LV_UUID)));
    }

    @Test
    void surfacesGatewayErrorOnDeleteFailure() {
        wm.stubFor(delete(urlEqualTo("/api/list-views/" + LV_UUID))
                .willReturn(aResponse().withStatus(404).withBody(
                        "{\"errors\":[{\"detail\":\"not found\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_listview",
                        Map.of("id", LV_UUID), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
