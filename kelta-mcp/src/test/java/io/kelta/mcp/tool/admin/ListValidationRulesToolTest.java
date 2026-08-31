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

class ListValidationRulesToolTest {

    private static final String COLLECTION_ID = "aaaaaaaa-1111-1111-1111-111111111111";

    private WireMockServer wm;
    private ListValidationRulesTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new ListValidationRulesTool(client);
        RequestPatHolder.set("klt_list_vr");
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=orders"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"type\":\"collections\",\"id\":\"" + COLLECTION_ID + "\"}]}")));
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutCollectionName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_validation_rules", Map.of(), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("collectionName");
    }

    @Test
    void rejectsUnknownCollection() {
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=unknown"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_validation_rules",
                        Map.of("collectionName", "unknown"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("not found");
    }

    @Test
    void listsByCollectionId() {
        wm.stubFor(get(urlEqualTo(
                "/api/validation-rules?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=200"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"id\":\"r1\",\"type\":\"validation-rules\"}]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_validation_rules",
                        Map.of("collectionName", "orders"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text()).contains("\"id\":\"r1\"");
        wm.verify(WireMock.getRequestedFor(urlEqualTo(
                "/api/validation-rules?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=200")));
    }

    @Test
    void respectsCustomPageSize() {
        wm.stubFor(get(urlEqualTo(
                "/api/validation-rules?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=50"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_validation_rules",
                        Map.of("collectionName", "orders", "pageSize", 50), null));

        wm.verify(WireMock.getRequestedFor(urlEqualTo(
                "/api/validation-rules?filter[collectionId][eq]=" + COLLECTION_ID + "&page[size]=50")));
    }
}
