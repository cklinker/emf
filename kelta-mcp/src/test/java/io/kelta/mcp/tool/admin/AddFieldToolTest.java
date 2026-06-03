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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class AddFieldToolTest {

    private WireMockServer wm;
    private AddFieldTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new AddFieldTool(client);
        RequestPatHolder.set("klt_addfield");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutRequiredArgs() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of("collectionName", "projects"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsJsonApiBodyWithRequiredAttributes() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"f1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "deadline",
                        "type", "datetime",
                        "required", true), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withHeader("Authorization", equalTo("Bearer klt_addfield"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("fields")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionName", equalTo("projects")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldName", equalTo("deadline")))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("datetime")))
                .withRequestBody(matchingJsonPath("$.data.attributes.required", equalTo("true"))));
    }

    @Test
    void acceptsBooleanDefaultValueWithoutStringification() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "events",
                        "fieldName", "active",
                        "type", "boolean",
                        "defaultValue", true), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(equalToJson(
                        "{\"data\":{\"type\":\"fields\",\"attributes\":{"
                                + "\"collectionName\":\"events\","
                                + "\"fieldName\":\"active\","
                                + "\"type\":\"boolean\","
                                + "\"defaultValue\":true}}}")));
    }

    @Test
    void acceptsNumericDefaultValueWithoutStringification() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "events",
                        "fieldName", "durationMinutes",
                        "type", "integer",
                        "defaultValue", 360), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(equalToJson(
                        "{\"data\":{\"type\":\"fields\",\"attributes\":{"
                                + "\"collectionName\":\"events\","
                                + "\"fieldName\":\"durationMinutes\","
                                + "\"type\":\"integer\","
                                + "\"defaultValue\":360}}}")));
    }

    @Test
    void includesReferenceCollectionForReferenceType() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "owner",
                        "type", "reference",
                        "referenceCollection", "users"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.referenceCollection", equalTo("users"))));
    }

    @Test
    void rejectsVectorWithoutDimension() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "embedding",
                        "type", "vector"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsVectorWithDimensionOutOfRange() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "embedding",
                        "type", "vector",
                        "dimension", 20000), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsDimensionInFieldTypeConfigForVector() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"f1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "embedding",
                        "type", "vector",
                        "dimension", 1536), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("vector")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldTypeConfig.dimension",
                        equalTo("1536"))));
    }

    @Test
    void rewritesCamelCaseRichTextAliasToCanonicalSnakeCase() {
        // FieldLifecycleHook accepts "rich_text" but not "richText". MCP clients
        // sometimes default to camelCase; we normalize so admin UI feel parity
        // doesn't trip the gateway validation.
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "synopsis",
                        "type", "richText"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("rich_text"))));
    }

    @Test
    void passesThroughPlainTextType() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "synopsis",
                        "type", "text"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("text"))));
    }
}
