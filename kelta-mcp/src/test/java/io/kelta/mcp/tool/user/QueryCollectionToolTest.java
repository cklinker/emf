package io.kelta.mcp.tool.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class QueryCollectionToolTest {

    private WireMockServer wm;
    private QueryCollectionTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new QueryCollectionTool(client);
        RequestPatHolder.set("klt_q_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void buildQueryStringEncodesFiltersSortAndPaging() {
        String q = QueryCollectionTool.buildQueryString(Map.of(
                "filter", Map.of("status", Map.of("EQ", "ACTIVE")),
                "sort", "lastName,-createdAt",
                "pageSize", 50,
                "pageNumber", 2));

        // JSON:API uses literal brackets (the gateway's Tomcat is configured
        // to allow them in query strings). Field names and values are URL-encoded.
        assertThat(q).contains("filter[status][EQ]=ACTIVE");
        assertThat(q).contains("sort=lastName%2C-createdAt");
        assertThat(q).contains("page[size]=50");
        assertThat(q).contains("page[number]=2");
    }

    @Test
    void buildQueryStringDropsUnknownOperators() {
        // Defensive contract on the helper: anything that bypasses the
        // call-handler validation still produces a clean URL with the
        // unknown op stripped (rather than passed to the worker, which
        // would silently drop it and return a wrong result set).
        String q = QueryCollectionTool.buildQueryString(Map.of(
                "filter", Map.of("name", Map.of("BOGUS_OP", "x"))));

        assertThat(q).doesNotContain("BOGUS_OP");
    }

    @Test
    void buildQueryStringPassesThroughCaseInsensitiveOperators() {
        // ICONTAINS / ISTARTS / IENDS / IEQ are real worker operators; they
        // must survive buildQueryString unchanged.
        String q = QueryCollectionTool.buildQueryString(Map.of(
                "filter", Map.of("lastName", Map.of("ICONTAINS", "smith"))));

        assertThat(q).contains("filter[lastName][ICONTAINS]=smith");
    }

    @Test
    void validateFilterOperatorsAcceptsKnownOperators() {
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("status", Map.of("EQ", "ACTIVE")))).isNull();
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("price", Map.of("GTE", 100, "LTE", 1000)))).isNull();
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("phone", Map.of("ISNULL", "true")))).isNull();
    }

    @Test
    void validateFilterOperatorsRejectsUnsupportedNamesWithGuidance() {
        // Operators that look plausible but the worker doesn't recognize
        // — these used to be silently forwarded and dropped. Now the MCP
        // layer rejects them with a message that points at the workaround.
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("status", Map.of("IN", "ACTIVE,PENDING"))))
                .contains("IN")
                .contains("Workarounds");
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("total", Map.of("BETWEEN", "100,1000"))))
                .contains("BETWEEN")
                .contains("GTE+LTE");
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("name", Map.of("STARTSWITH", "Foo"))))
                .contains("STARTSWITH");
        assertThat(QueryCollectionTool.validateFilterOperators(
                Map.of("phone", Map.of("IS_NULL", "true"))))
                .contains("IS_NULL");
    }

    @Test
    void rejectsCallWithUnsupportedFilterOperator() {
        SyncToolSpecification spec = tool.toSpecification();
        CallToolRequest req = new CallToolRequest("query_collection", Map.of(
                "collection", "customers",
                "filter", Map.of("status", Map.of("BETWEEN", "A,B"))
        ), null);

        CallToolResult result = spec.callHandler().apply(null, req);

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        // No HTTP call should have escaped to the gateway.
        wm.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void rejectsCallWithoutCollection() {
        SyncToolSpecification spec = tool.toSpecification();
        CallToolRequest req = new CallToolRequest("query_collection", Map.of(), null);

        CallToolResult result = spec.callHandler().apply(null, req);

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        wm.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void forwardsThroughGatewayWithLiteralBracketQueryParams() {
        wm.stubFor(get(urlEqualTo("/api/users?filter[status][EQ]=ACTIVE&sort=lastName"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        SyncToolSpecification spec = tool.toSpecification();
        CallToolRequest req = new CallToolRequest("query_collection", Map.of(
                "collection", "users",
                "filter", Map.of("status", Map.of("EQ", "ACTIVE")),
                "sort", "lastName"
        ), null);

        CallToolResult result = spec.callHandler().apply(null, req);

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/users?filter[status][EQ]=ACTIVE&sort=lastName"))
                .withHeader("Authorization", equalTo("Bearer klt_q_test")));
    }
}
