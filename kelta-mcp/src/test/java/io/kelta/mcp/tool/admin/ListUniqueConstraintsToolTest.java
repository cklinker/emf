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

class ListUniqueConstraintsToolTest {

    private WireMockServer wm;
    private ListUniqueConstraintsTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new ListUniqueConstraintsTool(client);
        RequestPatHolder.set("klt_list_uc");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_unique_constraints", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("collectionName");
    }

    @Test
    void listsConstraintsForCollection() {
        wm.stubFor(get(urlEqualTo("/api/admin/collections/availabilities/unique-constraints"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "[{\"indexName\":\"uniq_availabilities_title_provider\","
                                + "\"fieldNames\":[\"title\",\"provider\"]}]")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_unique_constraints",
                        Map.of("collectionName", "availabilities"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("uniq_availabilities_title_provider");
        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/admin/collections/availabilities/unique-constraints")));
    }

    @Test
    void surfacesGatewayErrorOnNotFound() {
        wm.stubFor(get(urlEqualTo("/api/admin/collections/missing/unique-constraints"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"Collection not found\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_unique_constraints",
                        Map.of("collectionName", "missing"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
