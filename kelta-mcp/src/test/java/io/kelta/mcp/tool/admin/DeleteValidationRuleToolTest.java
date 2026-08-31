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

class DeleteValidationRuleToolTest {

    private static final String RULE_ID = "cccccccc-3333-3333-3333-333333333333";

    private WireMockServer wm;
    private DeleteValidationRuleTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteValidationRuleTool(client);
        RequestPatHolder.set("klt_delete_vr");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutRuleId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_validation_rule", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("ruleId");
    }

    @Test
    void deletesSuccessfully() {
        wm.stubFor(delete(urlEqualTo("/api/validation-rules/" + RULE_ID))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_validation_rule",
                        Map.of("ruleId", RULE_ID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("Deleted validation rule", RULE_ID, "204");
        wm.verify(WireMock.deleteRequestedFor(urlEqualTo("/api/validation-rules/" + RULE_ID)));
    }

    @Test
    void surfacesGatewayErrorOnNotFound() {
        wm.stubFor(delete(urlEqualTo("/api/validation-rules/" + RULE_ID))
                .willReturn(aResponse().withStatus(404).withBody(
                        "{\"errors\":[{\"title\":\"Not found\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_validation_rule",
                        Map.of("ruleId", RULE_ID), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
