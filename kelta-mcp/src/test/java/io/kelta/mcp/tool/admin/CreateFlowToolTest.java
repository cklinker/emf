package io.kelta.mcp.tool.admin;

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

class CreateFlowToolTest {

    private WireMockServer wm;
    private CreateFlowTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new CreateFlowTool(client);
        RequestPatHolder.set("klt_flow_create");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutDefinition() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_flow", Map.of(
                        "name", "f",
                        "triggerType", "manual"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsFlowWithDefinitionPassedThrough() {
        wm.stubFor(post(urlEqualTo("/api/flows"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"f1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_flow", Map.of(
                        "name", "OnboardCustomer",
                        "triggerType", "recordCreated",
                        "triggerConfig", Map.of("collectionName", "customers"),
                        "definition", Map.of(
                                "nodes", java.util.List.of(Map.of("type", "start"))),
                        "active", true), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/flows"))
                .withHeader("Authorization", equalTo("Bearer klt_flow_create"))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("OnboardCustomer")))
                .withRequestBody(matchingJsonPath("$.data.attributes.triggerType", equalTo("recordCreated")))
                .withRequestBody(matchingJsonPath("$.data.attributes.triggerConfig.collectionName", equalTo("customers")))
                .withRequestBody(matchingJsonPath("$.data.attributes.definition.nodes[0].type", equalTo("start")))
                .withRequestBody(matchingJsonPath("$.data.attributes.active", equalTo("true"))));
    }
}
