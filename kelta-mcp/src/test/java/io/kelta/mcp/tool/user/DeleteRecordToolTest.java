package io.kelta.mcp.tool.user;

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

class DeleteRecordToolTest {

    private WireMockServer wm;
    private DeleteRecordTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteRecordTool(client);
        RequestPatHolder.set("klt_delete_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsCallWithoutCollectionOrId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_record", Map.of("collection", "accounts"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void deletesAndConfirmsWithStatus() {
        wm.stubFor(delete(urlEqualTo("/api/accounts/a1"))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_record", Map.of(
                        "collection", "accounts",
                        "id", "a1"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("Deleted accounts/a1", "204");
        wm.verify(WireMock.deleteRequestedFor(urlEqualTo("/api/accounts/a1")));
    }

    @Test
    void notFoundBecomesIsErrorResult() {
        wm.stubFor(delete(urlEqualTo("/api/accounts/missing"))
                .willReturn(aResponse().withStatus(404).withBody("{\"errors\":[{\"detail\":\"not found\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_record", Map.of(
                        "collection", "accounts",
                        "id", "missing"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }
}
