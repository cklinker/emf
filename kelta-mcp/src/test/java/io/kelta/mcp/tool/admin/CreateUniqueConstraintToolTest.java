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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CreateUniqueConstraintToolTest {

    private WireMockServer wm;
    private CreateUniqueConstraintTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new CreateUniqueConstraintTool(client);
        RequestPatHolder.set("klt_create_unique");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsMissingCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("fieldNames", List.of("a", "b")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("collectionName");
    }

    @Test
    void rejectsMissingFieldNames() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("fieldNames");
    }

    @Test
    void rejectsEmptyFieldNames() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability", "fieldNames", List.of()), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsBlankFieldName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability",
                                "fieldNames", java.util.Arrays.asList("title", " ")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsToWorkerAdminEndpointAndSurfacesBody() {
        wm.stubFor(post(urlEqualTo("/api/admin/collections/availability/unique-constraints"))
                .withRequestBody(equalToJson(
                        "{\"fieldNames\":[\"title\",\"provider\",\"region\"]}"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"indexName\":\"uniq_availability_title_provider_region\","
                                + "\"fieldNames\":[\"title\",\"provider\",\"region\"],"
                                + "\"columns\":[\"title\",\"provider\",\"region\"]}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability",
                                "fieldNames", List.of("title", "provider", "region")), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("uniq_availability_title_provider_region");
        wm.verify(WireMock.postRequestedFor(
                urlEqualTo("/api/admin/collections/availability/unique-constraints")));
    }

    @Test
    void surfacesGatewayErrorOnFailure() {
        wm.stubFor(post(urlEqualTo("/api/admin/collections/availability/unique-constraints"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"error\":\"Existing rows violate the proposed constraint\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability",
                                "fieldNames", List.of("title", "provider")), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("409");
    }
}
