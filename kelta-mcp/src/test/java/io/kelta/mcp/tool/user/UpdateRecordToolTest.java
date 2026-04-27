package io.kelta.mcp.tool.user;

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
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateRecordToolTest {

    private WireMockServer wm;
    private UpdateRecordTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new UpdateRecordTool(client);
        RequestPatHolder.set("klt_update_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsCallWithoutId() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_record", Map.of(
                        "collection", "accounts",
                        "attributes", Map.of("name", "Acme2")), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsCallWithoutAttrsOrRelationships() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_record", Map.of(
                        "collection", "accounts",
                        "id", "a1"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void patchesWithJsonApiBodyAndIncludesId() {
        wm.stubFor(patch(urlEqualTo("/api/accounts/a1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":{}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_record", Map.of(
                        "collection", "accounts",
                        "id", "a1",
                        "attributes", Map.of("name", "Acme2")), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.patchRequestedFor(urlEqualTo("/api/accounts/a1"))
                .withHeader("Authorization", equalTo("Bearer klt_update_test"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("accounts")))
                .withRequestBody(matchingJsonPath("$.data.id", equalTo("a1")))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("Acme2"))));
    }

    @Test
    void allowsRelationshipsOnlyUpdate() {
        wm.stubFor(patch(urlEqualTo("/api/contacts/c1"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":{}}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("update_record", Map.of(
                        "collection", "contacts",
                        "id", "c1",
                        "relationships", Map.of(
                                "account", Map.of("data", Map.of("type", "accounts", "id", "a2")))
                ), null));

        wm.verify(WireMock.patchRequestedFor(urlEqualTo("/api/contacts/c1"))
                .withRequestBody(matchingJsonPath("$.data.relationships.account.data.id", equalTo("a2"))));
    }
}
