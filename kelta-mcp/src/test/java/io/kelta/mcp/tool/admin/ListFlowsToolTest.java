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

class ListFlowsToolTest {

    private WireMockServer wm;
    private ListFlowsTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new ListFlowsTool(client);
        RequestPatHolder.set("klt_list_flows");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void listsAllWithoutFilter() {
        wm.stubFor(get(urlEqualTo("/api/flows?page[size]=200"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[{\"id\":\"f1\",\"type\":\"flows\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_flows", Map.of(), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"id\":\"f1\"");
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/flows?page[size]=200")));
    }

    @Test
    void appliesNameFilter() {
        wm.stubFor(get(urlEqualTo("/api/flows?page[size]=200&filter[name][EQ]=nightly"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_flows",
                        Map.of("name", "nightly"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/flows?page[size]=200&filter[name][EQ]=nightly")));
    }

    @Test
    void appliesPageSize() {
        wm.stubFor(get(urlEqualTo("/api/flows?page[size]=50"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_flows",
                        Map.of("pageSize", 50), null));

        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/flows?page[size]=50")));
    }

    @Test
    void clampsPageSize() {
        wm.stubFor(get(urlEqualTo("/api/flows?page[size]=200"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_flows",
                        Map.of("pageSize", 9999), null));

        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/flows?page[size]=200")));
    }
}
