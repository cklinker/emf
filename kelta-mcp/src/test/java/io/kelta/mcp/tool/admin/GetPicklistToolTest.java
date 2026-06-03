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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetPicklistToolTest {

    private static final String PICKLIST_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private WireMockServer wm;
    private GetPicklistTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new GetPicklistTool(client);
        RequestPatHolder.set("klt_get_picklist");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsMissingPicklist() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_picklist", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void fetchesByUuidWithValues() {
        wm.stubFor(get(urlEqualTo("/api/global-picklists/" + PICKLIST_UUID))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":{\"id\":\"" + PICKLIST_UUID + "\",\"attributes\":{\"name\":\"stages\"}}}")));
        wm.stubFor(get(urlEqualTo("/api/global-picklists/" + PICKLIST_UUID + "/picklist-values"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"id\":\"v1\",\"attributes\":{\"value\":\"OPEN\"}}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_picklist",
                        Map.of("picklist", PICKLIST_UUID), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("stages", "OPEN");
        wm.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/global-picklists")));
    }

    @Test
    void resolvesNameToIdBeforeFetching() {
        wm.stubFor(get(urlEqualTo(
                "/api/global-picklists?filter[name][EQ]=stages&page[size]=1"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"id\":\"" + PICKLIST_UUID + "\",\"attributes\":{\"name\":\"stages\"}}]}")));
        wm.stubFor(get(urlEqualTo("/api/global-picklists/" + PICKLIST_UUID))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":{\"id\":\"" + PICKLIST_UUID + "\"}}")));
        wm.stubFor(get(urlEqualTo("/api/global-picklists/" + PICKLIST_UUID + "/picklist-values"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_picklist",
                        Map.of("picklist", "stages"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains(PICKLIST_UUID);
    }

    @Test
    void reportsErrorWhenNameLookupReturnsEmpty() {
        wm.stubFor(get(urlEqualTo(
                "/api/global-picklists?filter[name][EQ]=missing&page[size]=1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("get_picklist",
                        Map.of("picklist", "missing"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("No picklist found", "missing");
    }
}
