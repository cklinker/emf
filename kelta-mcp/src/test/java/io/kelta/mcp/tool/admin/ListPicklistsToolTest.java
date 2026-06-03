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

class ListPicklistsToolTest {

    private WireMockServer wm;
    private ListPicklistsTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new ListPicklistsTool(client);
        RequestPatHolder.set("klt_list_picklists");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void listsAllWhenNoFilter() {
        wm.stubFor(get(urlEqualTo("/api/global-picklists"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_picklists", Map.of(), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("data");
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/global-picklists")));
    }

    @Test
    void appliesNameFilterWhenProvided() {
        wm.stubFor(get(urlEqualTo("/api/global-picklists?filter[name][EQ]=stages"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"id\":\"p1\",\"attributes\":{\"name\":\"stages\"}}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_picklists",
                        Map.of("name", "stages"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("stages");
        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/global-picklists?filter[name][EQ]=stages")));
    }

    @Test
    void surfacesGatewayErrorOnList() {
        wm.stubFor(get(urlEqualTo("/api/global-picklists"))
                .willReturn(aResponse().withStatus(500).withBody("server down")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_picklists", Map.of(), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("500");
    }
}
