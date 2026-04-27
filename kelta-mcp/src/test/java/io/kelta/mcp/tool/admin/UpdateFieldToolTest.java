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
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateFieldToolTest {

    private WireMockServer wm;
    private UpdateFieldTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new UpdateFieldTool(client);
        RequestPatHolder.set("klt_upd_field");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_field", Map.of("required", true), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsWhenNoFieldsToUpdate() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_field", Map.of("id", "f1"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void patchesIncludingIdAndType() {
        wm.stubFor(patch(urlEqualTo("/api/fields/f1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":{}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_field", Map.of(
                        "id", "f1",
                        "required", true,
                        "description", "Now required"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.patchRequestedFor(urlEqualTo("/api/fields/f1"))
                .withHeader("Authorization", equalTo("Bearer klt_upd_field"))
                .withRequestBody(matchingJsonPath("$.data.id", equalTo("f1")))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("fields")))
                .withRequestBody(matchingJsonPath("$.data.attributes.required", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.data.attributes.description", equalTo("Now required"))));
    }
}
