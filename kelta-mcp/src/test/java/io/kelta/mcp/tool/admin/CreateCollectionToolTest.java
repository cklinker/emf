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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CreateCollectionToolTest {

    private WireMockServer wm;
    private CreateCollectionTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000));
        tool = new CreateCollectionTool(client);
        RequestPatHolder.set("klt_create_coll");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_collection", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void createsCollectionOnlyWhenNoFieldsSupplied() {
        wm.stubFor(post(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(201)
                        .withBody("{\"data\":{\"id\":\"c1\",\"type\":\"collections\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_collection", Map.of(
                        "name", "projects",
                        "displayFieldName", "name",
                        "description", "Project tracker"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/collections"))
                .withHeader("Authorization", equalTo("Bearer klt_create_coll"))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("projects")))
                .withRequestBody(matchingJsonPath("$.data.attributes.displayFieldName", equalTo("name"))));
        wm.verify(0, WireMock.postRequestedFor(urlEqualTo("/api/fields")));
    }

    @Test
    void createsCollectionThenEachField() {
        wm.stubFor(post(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(201)
                        .withBody("{\"data\":{\"id\":\"c1\"}}")));
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"f\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_collection", Map.of(
                        "name", "projects",
                        "fields", List.of(
                                Map.of("fieldName", "name", "type", "text", "required", true),
                                Map.of("fieldName", "owner", "type", "reference",
                                        "referenceCollection", "users"))), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("collection", "fields");
        wm.verify(2, WireMock.postRequestedFor(urlEqualTo("/api/fields")));
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldName", equalTo("name")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionName", equalTo("projects"))));
    }

    @Test
    void shortCircuitsWhenCollectionCreationFails() {
        wm.stubFor(post(urlEqualTo("/api/collections"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"errors\":[{\"detail\":\"already exists\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_collection", Map.of(
                        "name", "projects",
                        "fields", List.of(Map.of("fieldName", "name", "type", "text"))), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        wm.verify(0, WireMock.postRequestedFor(urlEqualTo("/api/fields")));
    }
}
