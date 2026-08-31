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

class DeleteFlowToolTest {

    private static final String FLOW_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private WireMockServer wm;
    private DeleteFlowTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteFlowTool(client);
        RequestPatHolder.set("klt_delete_flow");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_flow", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void deletesDirectlyWhenInputLooksLikeUuid() {
        wm.stubFor(delete(urlEqualTo("/api/flows/" + FLOW_UUID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_flow",
                        Map.of("id", FLOW_UUID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("Deleted flow", FLOW_UUID, "204");
        wm.verify(0, WireMock.getRequestedFor(urlPathEqualTo("/api/flows")));
    }

    @Test
    void resolvesNameToIdBeforeDeleting() {
        wm.stubFor(get(urlEqualTo(
                "/api/flows?filter[name][EQ]=nightly-ingest&page[size]=1"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"type\":\"flows\",\"id\":\"" + FLOW_UUID + "\"}]}")));
        wm.stubFor(delete(urlEqualTo("/api/flows/" + FLOW_UUID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_flow",
                        Map.of("id", "nightly-ingest"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.deleteRequestedFor(urlEqualTo("/api/flows/" + FLOW_UUID)));
    }

    @Test
    void reportsErrorWhenNameLookupReturnsEmpty() {
        wm.stubFor(get(urlEqualTo(
                "/api/flows?filter[name][EQ]=missing&page[size]=1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_flow",
                        Map.of("id", "missing"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("No flow found", "missing");
    }

    @Test
    void surfacesGatewayErrorForFlowWithRunHistory() {
        // Flows with run history may return 409 from the gateway — surface as-is.
        wm.stubFor(delete(urlEqualTo("/api/flows/" + FLOW_UUID))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"errors\":[{\"detail\":\"Flow has run history and cannot be deleted\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_flow",
                        Map.of("id", FLOW_UUID), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("409");
    }
}
