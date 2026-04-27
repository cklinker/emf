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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CreatePicklistToolTest {

    private WireMockServer wm;
    private CreatePicklistTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new CreatePicklistTool(client);
        RequestPatHolder.set("klt_picklist_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsEmptyValuesArray() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_picklist", Map.of(
                        "name", "stages",
                        "values", List.of()), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void createsPicklistThenEachValueWithSortOrder() {
        wm.stubFor(post(urlEqualTo("/api/globalPicklists"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"p1\"}}")));
        wm.stubFor(post(urlEqualTo("/api/picklistValues"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"pv\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_picklist", Map.of(
                        "name", "stages",
                        "values", List.of(
                                Map.of("value", "OPEN", "label", "Open"),
                                Map.of("value", "CLOSED", "label", "Closed"))), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(2, WireMock.postRequestedFor(urlEqualTo("/api/picklistValues")));
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/picklistValues"))
                .withHeader("Authorization", equalTo("Bearer klt_picklist_test"))
                .withRequestBody(matchingJsonPath("$.data.attributes.value", equalTo("OPEN")))
                .withRequestBody(matchingJsonPath("$.data.attributes.picklistName", equalTo("stages")))
                .withRequestBody(matchingJsonPath("$.data.attributes.sortOrder", equalTo("0"))));
    }
}
