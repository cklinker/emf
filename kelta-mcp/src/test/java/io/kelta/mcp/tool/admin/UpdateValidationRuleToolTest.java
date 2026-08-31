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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateValidationRuleToolTest {

    private static final String RULE_ID = "11111111-aaaa-bbbb-cccc-dddddddddddd";

    private WireMockServer wm;
    private UpdateValidationRuleTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new UpdateValidationRuleTool(client);
        RequestPatHolder.set("klt_update_vr");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_validation_rule", Map.of("active", true), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"id\"");
    }

    @Test
    void rejectsWithNoAttributes() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_validation_rule", Map.of("id", RULE_ID), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void patchesActiveFlag() {
        wm.stubFor(patch(urlEqualTo("/api/validation-rules/" + RULE_ID))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"id\":\"" + RULE_ID + "\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_validation_rule",
                        Map.of("id", RULE_ID, "active", false), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.patchRequestedFor(urlEqualTo("/api/validation-rules/" + RULE_ID))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("validation-rules")))
                .withRequestBody(matchingJsonPath("$.data.id", equalTo(RULE_ID)))
                .withRequestBody(matchingJsonPath("$.data.attributes.active", equalTo("false"))));
    }

    @Test
    void surfacesGatewayErrorOnNotFound() {
        wm.stubFor(patch(urlEqualTo("/api/validation-rules/" + RULE_ID))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"Not found\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_validation_rule",
                        Map.of("id", RULE_ID, "name", "new-name"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
