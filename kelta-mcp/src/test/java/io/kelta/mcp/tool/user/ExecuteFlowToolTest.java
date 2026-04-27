package io.kelta.mcp.tool.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ExecuteFlowToolTest {

    private WireMockServer wm;
    private ExecuteFlowTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000));
        tool = new ExecuteFlowTool(client);
        RequestPatHolder.set("klt_flow_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsCallWithoutFlowId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("execute_flow", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsExecuteWithEmptyBodyWhenNoInput() {
        wm.stubFor(post(urlEqualTo("/api/flows/flow-123/execute"))
                .willReturn(aResponse().withStatus(202).withBody("{\"executionId\":\"e1\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("execute_flow", Map.of("flowId", "flow-123"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/flows/flow-123/execute"))
                .withHeader("Authorization", equalTo("Bearer klt_flow_test")));
    }

    @Test
    void passesInputThroughAsJsonBody() {
        wm.stubFor(post(urlEqualTo("/api/flows/flow-123/execute"))
                .willReturn(aResponse().withStatus(202).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("execute_flow", Map.of(
                        "flowId", "flow-123",
                        "input", Map.of("ticketId", "T-99", "priority", "high")), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/flows/flow-123/execute"))
                .withRequestBody(matchingJsonPath("$.ticketId", equalTo("T-99")))
                .withRequestBody(matchingJsonPath("$.priority", equalTo("high"))));
    }
}
