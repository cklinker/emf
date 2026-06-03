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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class AddFieldToolTest {

    private static final String COLLECTION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String LOOKUP_FILTER = "/api/collections?filter[name][eq]=projects";

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
    void mapsFriendlyAliasToNativeUppercaseType() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"f1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "summary",
                        "type", "text",
                        "required", true), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withHeader("Authorization", equalTo("Bearer klt_addfield"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("fields")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionId", equalTo(COLLECTION_ID)))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("summary")))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("STRING")))
                .withRequestBody(matchingJsonPath("$.data.attributes.required", equalTo("true"))));
    }

    @Test
    void mapsNumberAliasToInteger() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "count",
                        "type", "number"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("INTEGER"))));
    }

    @Test
    void translatesUniqueToUniqueConstraint() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "code",
                        "type", "text",
                        "unique", true), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.uniqueConstraint", equalTo("true"))));
    }

    @Test
    void exposesDisplayNameIndexedSearchable() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "name",
                        "type", "text",
                        "displayName", "Project Name",
                        "indexed", true,
                        "searchable", true), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.displayName", equalTo("Project Name")))
                .withRequestBody(matchingJsonPath("$.data.attributes.indexed", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.data.attributes.searchable", equalTo("true"))));
    }

    @Test
    void usesCollectionIdDirectlyWhenProvided() {
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionId", COLLECTION_ID,
                        "fieldName", "deadline",
                        "type", "datetime"), null));

        wm.verify(0, WireMock.getRequestedFor(urlPathEqualTo("/api/collections")));
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionId", equalTo(COLLECTION_ID)))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("DATETIME"))));
    }

    @Test
    void picklistAttachesFieldTypeConfig() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        String picklistId = "22222222-2222-2222-2222-222222222222";

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "status",
                        "type", "picklist",
                        "picklistSourceId", picklistId), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("PICKLIST")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldTypeConfig.picklistSourceType", equalTo("GLOBAL")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldTypeConfig.picklistSourceId", equalTo(picklistId))));
    }

    @Test
    void picklistRequiresSourceId() {
        stubCollectionLookup();

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "status",
                        "type", "picklist"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void referenceBuildsRelationshipAndRelationshipName() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        String targetId = "33333333-3333-3333-3333-333333333333";

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "owner",
                        "type", "reference",
                        "referenceCollectionId", targetId,
                        "relationshipName", "Owner"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("LOOKUP")))
                .withRequestBody(matchingJsonPath("$.data.attributes.relationshipName", equalTo("Owner")))
                .withRequestBody(matchingJsonPath("$.data.relationships.referenceCollectionId.data.id", equalTo(targetId)))
                .withRequestBody(matchingJsonPath("$.data.relationships.referenceCollectionId.data.type", equalTo("collections"))));
    }

    @Test
    void referenceAcceptsOptionalRelationshipType() {
        stubCollectionLookup();
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        String targetId = "33333333-3333-3333-3333-333333333333";

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "account",
                        "type", "reference",
                        "referenceCollectionId", targetId,
                        "relationshipName", "Account",
                        "relationshipType", "master_detail"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.relationshipType", equalTo("MASTER_DETAIL"))));
    }

    @Test
    void referenceRequiresTargetCollection() {
        stubCollectionLookup();

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "owner",
                        "type", "reference"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsUnknownTypeAlias() {
        stubCollectionLookup();

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "projects",
                        "fieldName", "x",
                        "type", "blob"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void extractFirstIdReadsJsonApiListResponse() {
        String body = "{\"data\":[{\"type\":\"collections\",\"id\":\"abc-123\",\"attributes\":{}}]}";
        assertThat(FieldBodyBuilder.extractFirstId(body)).isEqualTo("abc-123");
    }

    @Test
    void extractFirstIdReturnsNullForEmptyList() {
        assertThat(FieldBodyBuilder.extractFirstId("{\"data\":[]}")).isNull();
    }

    private void stubCollectionLookup() {
        stubCollectionLookup("projects");
    }

    private void stubCollectionLookup(String name) {
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=" + name))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":[{\"type\":\"collections\",\"id\":\"" + COLLECTION_ID + "\"}]}")));
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
        stubCollectionLookup("titles");
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
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("VECTOR")))
                .withRequestBody(matchingJsonPath("$.data.attributes.fieldTypeConfig.dimension",
                        equalTo("1536"))));
    }

    @Test
    void rewritesCamelCaseRichTextAliasToCanonicalSnakeCase() {
        // FieldLifecycleHook accepts "rich_text" but not "richText". MCP clients
        // sometimes default to camelCase; we normalize so admin UI feel parity
        // doesn't trip the gateway validation.
        stubCollectionLookup("titles");
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "synopsis",
                        "type", "richText"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("RICH_TEXT"))));
    }

    @Test
    void mapsTextAliasToString() {
        stubCollectionLookup("titles");
        wm.stubFor(post(urlEqualTo("/api/fields"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("add_field", Map.of(
                        "collectionName", "titles",
                        "fieldName", "synopsis",
                        "type", "text"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/fields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.type", equalTo("STRING"))));
    }
}
