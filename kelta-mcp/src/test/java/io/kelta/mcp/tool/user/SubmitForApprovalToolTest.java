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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class SubmitForApprovalToolTest {

    private WireMockServer wm;
    private SubmitForApprovalTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new SubmitForApprovalTool(client);
        RequestPatHolder.set("klt_submit_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsCallWithoutCollectionOrRecord() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("submit_for_approval", Map.of("collectionId", "c1"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsCollectionAndRecordIds() {
        wm.stubFor(post(urlEqualTo("/api/approvals/submit"))
                .willReturn(aResponse().withStatus(200).withBody("{\"instanceId\":\"i1\"}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("submit_for_approval", Map.of(
                        "collectionId", "c1",
                        "recordId", "r1"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/approvals/submit"))
                .withHeader("Authorization", equalTo("Bearer klt_submit_test"))
                .withRequestBody(matchingJsonPath("$.collectionId", equalTo("c1")))
                .withRequestBody(matchingJsonPath("$.recordId", equalTo("r1"))));
    }

    @Test
    void includesProcessIdWhenProvided() {
        wm.stubFor(post(urlEqualTo("/api/approvals/submit"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("submit_for_approval", Map.of(
                        "collectionId", "c1",
                        "recordId", "r1",
                        "processId", "p1"), null));

        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/approvals/submit"))
                .withRequestBody(matchingJsonPath("$.processId", equalTo("p1"))));
    }
}
