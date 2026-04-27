package io.kelta.mcp.tool.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.config.McpProperties;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class ListApprovalsToolTest {

    private WireMockServer wm;
    private ListApprovalsTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new ListApprovalsTool(client);
        RequestPatHolder.set("klt_listapproval_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void defaultsToPendingFilter() {
        wm.stubFor(get(urlEqualTo("/api/approvalInstances?filter[status][EQ]=PENDING"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_approvals", Map.of(), null));

        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/approvalInstances?filter[status][EQ]=PENDING")));
    }

    @Test
    void allStatusOmitsTheFilter() {
        wm.stubFor(get(urlEqualTo("/api/approvalInstances"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_approvals", Map.of("status", "all"), null));

        wm.verify(WireMock.getRequestedFor(urlEqualTo("/api/approvalInstances")));
    }

    @Test
    void appendsPagingParams() {
        wm.stubFor(get(urlEqualTo("/api/approvalInstances?filter[status][EQ]=APPROVED&page[size]=50&page[number]=2"))
                .willReturn(aResponse().withStatus(200).withBody("{\"data\":[]}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("list_approvals", Map.of(
                        "status", "approved",
                        "pageSize", 50,
                        "pageNumber", 2), null));

        wm.verify(WireMock.getRequestedFor(
                urlEqualTo("/api/approvalInstances?filter[status][EQ]=APPROVED&page[size]=50&page[number]=2")));
    }
}
