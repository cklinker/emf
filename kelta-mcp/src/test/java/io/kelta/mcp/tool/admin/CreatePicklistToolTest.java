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
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
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
    void createsPicklistThenEachValueWithFlatSourceAttributes() {
        wm.stubFor(post(urlEqualTo("/api/global-picklists"))
                .willReturn(aResponse().withStatus(201)
                        .withBody("{\"data\":{\"id\":\"p-123\",\"type\":\"global-picklists\"}}")));
        wm.stubFor(post(urlEqualTo("/api/picklist-values"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"pv\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_picklist", Map.of(
                        "name", "stages",
                        "values", List.of(
                                Map.of("value", "OPEN", "label", "Open"),
                                Map.of("value", "CLOSED", "label", "Closed"))), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/global-picklists"))
                .withHeader("Authorization", equalTo("Bearer klt_picklist_test"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("global-picklists")))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("stages"))));
        wm.verify(2, WireMock.postRequestedFor(urlEqualTo("/api/picklist-values")));
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/picklist-values"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("picklist-values")))
                .withRequestBody(matchingJsonPath("$.data.attributes.value", equalTo("OPEN")))
                .withRequestBody(matchingJsonPath("$.data.attributes.picklistSourceType", equalTo("GLOBAL")))
                .withRequestBody(matchingJsonPath("$.data.attributes.picklistSourceId", equalTo("p-123")))
                .withRequestBody(matchingJsonPath("$.data.attributes.sortOrder", equalTo("0")))
                .withRequestBody(matchingJsonPath("$.data.attributes.isActive", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.data.attributes.isDefault", equalTo("false"))));
    }

    @Test
    void normalizesLegacyActiveAttributeToIsActive() {
        wm.stubFor(post(urlEqualTo("/api/global-picklists"))
                .willReturn(aResponse().withStatus(201)
                        .withBody("{\"data\":{\"id\":\"p-1\"}}")));
        wm.stubFor(post(urlEqualTo("/api/picklist-values"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"pv\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_picklist", Map.of(
                        "name", "phases",
                        "values", List.of(
                                Map.of("value", "DONE", "label", "Done", "active", false))), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/picklist-values"))
                .withRequestBody(matchingJsonPath("$.data.attributes.isActive", equalTo("false"))));
    }
}
