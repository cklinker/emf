package io.kelta.mcp.tool.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
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

class GetCollectionSchemaToolTest {

    private static final String COLLECTION_BODY = """
            {"data":{"id":"ec000100-0000-0000-0000-000000000003",\
            "type":"collections",\
            "attributes":{"name":"customers","systemCollection":false}}}""";
    private static final String FIELDS_BODY = """
            {"metadata":{"totalCount":18},\
            "data":[{"id":"f1","type":"fields","attributes":{"name":"phone"}}]}""";

    private WireMockServer wm;
    private GetCollectionSchemaTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new GetCollectionSchemaTool(client);
        RequestPatHolder.set("klt_schema_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void extractCollectionIdParsesJsonApiSingleResource() {
        assertThat(GetCollectionSchemaTool.extractCollectionId(COLLECTION_BODY))
                .isEqualTo("ec000100-0000-0000-0000-000000000003");
    }

    @Test
    void extractCollectionIdReturnsNullForMalformedBodies() {
        assertThat(GetCollectionSchemaTool.extractCollectionId(null)).isNull();
        assertThat(GetCollectionSchemaTool.extractCollectionId("")).isNull();
        assertThat(GetCollectionSchemaTool.extractCollectionId("not json")).isNull();
        assertThat(GetCollectionSchemaTool.extractCollectionId("{\"data\":{}}")).isNull();
    }

    @Test
    void fieldsAreFilteredByCollectionIdNotName() {
        // Regression: the previous version filtered on `filter[collectionName]`
        // which the worker rejected with HTTP 400 because no such column exists
        // on FieldDefinition. The fix looks up the collection, extracts its id,
        // and filters by `filter[collectionId]` — the actual foreign key.
        wm.stubFor(get(urlEqualTo("/api/collections/customers"))
                .willReturn(aResponse().withStatus(200).withBody(COLLECTION_BODY)));
        wm.stubFor(get(urlEqualTo(
                "/api/fields?filter[collectionId][EQ]=ec000100-0000-0000-0000-000000000003&page[size]=200"))
                .willReturn(aResponse().withStatus(200).withBody(FIELDS_BODY)));

        SyncToolSpecification spec = tool.toSpecification();
        CallToolResult result = spec.callHandler().apply(null,
                new CallToolRequest("get_collection_schema",
                        Map.of("collection", "customers"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text)
                .contains("\"collection\":")
                .contains("\"fields\":")
                .contains("phone");

        // Pin: the second outbound request must use the collectionId UUID
        // filter, not the old (broken) collectionName filter.
        wm.verify(WireMock.getRequestedFor(urlEqualTo(
                "/api/fields?filter[collectionId][EQ]=ec000100-0000-0000-0000-000000000003&page[size]=200")));
        wm.verify(0, WireMock.getRequestedFor(WireMock.urlMatching(
                "/api/fields\\?filter\\[collectionName\\]\\[EQ\\]=.*")));
    }

    @Test
    void surfacesNullFieldsWhenIdCannotBeExtracted() {
        // If the collection response is missing a parseable data.id, we
        // skip the fields fetch and return fields=null rather than
        // forwarding a bogus filter or crashing.
        wm.stubFor(get(urlEqualTo("/api/collections/customers"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":{}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(null,
                new CallToolRequest("get_collection_schema",
                        Map.of("collection", "customers"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("\"fields\": null");
        // No second request — we don't know what to filter by.
        wm.verify(0, WireMock.getRequestedFor(WireMock.urlMatching("/api/fields.*")));
    }

    @Test
    void surfacesErrorWhenCollectionLookupFails() {
        wm.stubFor(get(urlEqualTo("/api/collections/nope"))
                .willReturn(aResponse().withStatus(404).withBody("{\"errors\":[{\"status\":\"404\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(null,
                new CallToolRequest("get_collection_schema",
                        Map.of("collection", "nope"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        // No fields fetch attempted.
        wm.verify(0, WireMock.getRequestedFor(WireMock.urlMatching("/api/fields.*")));
    }

    @Test
    void rejectsCallWithoutCollectionArg() {
        SyncToolSpecification spec = tool.toSpecification();
        CallToolResult result = spec.callHandler().apply(null,
                new CallToolRequest("get_collection_schema", Map.of(), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        wm.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }
}
