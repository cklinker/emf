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
        RequestPatHolder.set("klt_cuq");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWhenCollectionNameMissing() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("fieldNames", List.of("a", "b")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsWhenFieldNamesTooShort() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability",
                                "fieldNames", List.of("title")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsWhenFieldNamesEntryIsBlank() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("collectionName", "availability",
                                "fieldNames", List.of("title", "")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsJsonBodyToCompositeUniqueEndpoint() {
        wm.stubFor(post(urlEqualTo("/api/_composite-unique-constraints"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"collectionName\":\"availability\","
                                + "\"constraintName\":\"cuq_availability_deadbeef\","
                                + "\"fieldNames\":[\"title\",\"provider\",\"region\"]}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title", "provider", "region")), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/_composite-unique-constraints"))
                .withHeader("Authorization", equalTo("Bearer klt_cuq"))
                .withRequestBody(matchingJsonPath("$.collectionName", equalTo("availability")))
                .withRequestBody(matchingJsonPath("$.fieldNames[0]", equalTo("title")))
                .withRequestBody(matchingJsonPath("$.fieldNames[2]", equalTo("region"))));
    }

    @Test
    void surfacesGatewayErrorThroughMapper() {
        wm.stubFor(post(urlEqualTo("/api/_composite-unique-constraints"))
                .willReturn(aResponse().withStatus(400).withBody(
                        "{\"errors\":[{\"detail\":\"Field 'ghost' is not defined\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title", "ghost")), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }
}
