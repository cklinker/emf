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

class DeleteUniqueConstraintToolTest {

    private static final String INDEX_NAME = "uniq_availability_title_provider";

    private WireMockServer wm;
    private DeleteUniqueConstraintTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new DeleteUniqueConstraintTool(client);
        RequestPatHolder.set("klt_delete_uc");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_unique_constraint",
                        Map.of("indexName", INDEX_NAME), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("collectionName");
    }

    @Test
    void rejectsWithoutIndexName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_unique_constraint",
                        Map.of("collectionName", "availability"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("indexName");
    }

    @Test
    void dropsConstraintSuccessfully() {
        wm.stubFor(delete(urlEqualTo(
                "/api/admin/collections/availability/unique-constraints/" + INDEX_NAME))
                .willReturn(aResponse().withStatus(204)));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_unique_constraint",
                        Map.of("collectionName", "availability", "indexName", INDEX_NAME), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("Dropped constraint", INDEX_NAME, "availability", "204");
        wm.verify(WireMock.deleteRequestedFor(urlEqualTo(
                "/api/admin/collections/availability/unique-constraints/" + INDEX_NAME)));
    }

    @Test
    void surfacesGatewayErrorOnNotFound() {
        wm.stubFor(delete(urlEqualTo(
                "/api/admin/collections/availability/unique-constraints/" + INDEX_NAME))
                .willReturn(aResponse().withStatus(404).withBody(
                        "{\"errors\":[{\"title\":\"Index not found\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("delete_unique_constraint",
                        Map.of("collectionName", "availability", "indexName", INDEX_NAME), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("404");
    }
}
