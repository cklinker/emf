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
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ImportApiSpecToolTest {

    private WireMockServer wm;
    private ImportApiSpecTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), "", 30, 60_000, null));
        tool = new ImportApiSpecTool(client);
        RequestPatHolder.set("klt_apispec_import");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutNameOrRaw() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("import_api_spec", Map.of("name", "Stripe"), null));
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void postsRawSpecToImport() {
        wm.stubFor(post(urlEqualTo("/api/api-specs/import"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"id\":\"s1\",\"diff\":{\"added\":[]}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("import_api_spec", Map.of(
                        "name", "Stripe",
                        "raw", "{\"openapi\":\"3.0.0\"}",
                        "sourceUrl", "https://stripe.com/openapi.json"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/api-specs/import"))
                .withHeader("Authorization", equalTo("Bearer klt_apispec_import"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("Stripe")))
                .withRequestBody(matchingJsonPath("$.raw", equalTo("{\"openapi\":\"3.0.0\"}")))
                .withRequestBody(matchingJsonPath("$.sourceUrl", equalTo("https://stripe.com/openapi.json"))));
    }
}
