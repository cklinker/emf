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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetFlowRunToolTest {

    private WireMockServer wm;
    private GetFlowRunTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000));
        tool = new GetFlowRunTool(client);
        RequestPatHolder.set("klt_flowrun_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void fetchesExecutionWithoutStepsByDefault() {
        wm.stubFor(get(urlEqualTo("/api/flows/executions/e1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"RUNNING\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_flow_run", Map.of("executionId", "e1"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/flows/executions/e1")));
        wm.verify(0, WireMock.getRequestedFor(urlEqualTo("/api/flows/executions/e1/steps")));
    }

    @Test
    void mergesStepsWhenIncludeStepsTrue() {
        wm.stubFor(get(urlEqualTo("/api/flows/executions/e1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"DONE\"}")));
        wm.stubFor(get(urlEqualTo("/api/flows/executions/e1/steps"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"stepId\":\"s1\"}]")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_flow_run", Map.of(
                        "executionId", "e1",
                        "includeSteps", true), null));

        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("\"execution\":", "\"steps\":", "DONE", "s1");
    }
}
