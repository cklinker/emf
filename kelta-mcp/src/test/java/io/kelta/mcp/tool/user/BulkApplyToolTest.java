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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class BulkApplyToolTest {

    private WireMockServer wm;
    private BulkApplyTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new BulkApplyTool(client);
        RequestPatHolder.set("klt_bulk_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsEmptyOperations() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("bulk_apply", Map.of("operations", List.of()), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void rejectsUnknownOpName() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("bulk_apply", Map.of("operations", List.of(
                        Map.of("op", "destroy", "type", "accounts", "id", "a1"))), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void requiresIdForUpdateAndRemove() {
        CallToolResult update = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("bulk_apply", Map.of("operations", List.of(
                        Map.of("op", "update", "type", "accounts",
                                "attributes", Map.of("name", "x")))), null));
        assertThat(update.isError()).isEqualTo(Boolean.TRUE);

        CallToolResult remove = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("bulk_apply", Map.of("operations", List.of(
                        Map.of("op", "remove", "type", "accounts"))), null));
        assertThat(remove.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsAtomicOperationsBundle() {
        wm.stubFor(post(urlEqualTo("/api/_atomic"))
                .willReturn(aResponse().withStatus(200).withBody("{\"atomic:results\":[]}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("bulk_apply", Map.of("operations", List.of(
                        Map.of("op", "add", "type", "accounts",
                                "attributes", Map.of("name", "Acme")),
                        Map.of("op", "update", "type", "accounts", "id", "a2",
                                "attributes", Map.of("name", "Updated")),
                        Map.of("op", "remove", "type", "accounts", "id", "a3")
                )), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/_atomic"))
                .withHeader("Authorization", equalTo("Bearer klt_bulk_test"))
                .withRequestBody(matchingJsonPath("$.['atomic:operations'][0].op", equalTo("add")))
                .withRequestBody(matchingJsonPath("$.['atomic:operations'][1].op", equalTo("update")))
                .withRequestBody(matchingJsonPath("$.['atomic:operations'][2].op", equalTo("remove"))));
    }
}
