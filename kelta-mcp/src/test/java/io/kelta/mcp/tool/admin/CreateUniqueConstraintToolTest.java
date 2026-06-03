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
        RequestPatHolder.set("klt_unique");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint",
                        Map.of("fieldNames", List.of("a", "b")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsFewerThanTwoFields() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsBlankFieldNames() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title", "")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsJsonApiBodyToTheGateway() {
        wm.stubFor(post(urlEqualTo("/api/_composite-unique-constraints"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"id\":\"cuq_availability_abc\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title", "provider", "region")), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/_composite-unique-constraints"))
                .withHeader("Authorization", equalTo("Bearer klt_unique"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("compositeUniqueConstraints")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionName",
                        equalTo("availability")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldNames[0]",
                        equalTo("title")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldNames[2]",
                        equalTo("region"))));
    }

    @Test
    void surfacesGatewayErrorsAsToolErrors() {
        wm.stubFor(post(urlEqualTo("/api/_composite-unique-constraints"))
                .willReturn(aResponse().withStatus(400).withBody(
                        "{\"errors\":[{\"detail\":\"field 'ghost' does not exist\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_unique_constraint", Map.of(
                        "collectionName", "availability",
                        "fieldNames", List.of("title", "ghost")), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }
}
