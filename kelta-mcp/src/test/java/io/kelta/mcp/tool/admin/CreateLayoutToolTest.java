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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CreateLayoutToolTest {

    private WireMockServer wm;
    private CreateLayoutTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new CreateLayoutTool(client);
        RequestPatHolder.set("klt_layout_test");
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void rejectsWithoutNameOrCollection() {
        CallToolResult r1 = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_layout", Map.of(
                        "collectionName", "projects",
                        "sections", List.of()), null));
        assertThat(r1.isError()).isEqualTo(Boolean.TRUE);

        CallToolResult r2 = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_layout", Map.of(
                        "name", "main",
                        "sections", List.of()), null));
        assertThat(r2.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void createsLayoutSectionsAndFieldsInOrder() {
        wm.stubFor(post(urlEqualTo("/api/pageLayouts"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"id\":\"L1\",\"type\":\"pageLayouts\"}}")));
        wm.stubFor(post(urlEqualTo("/api/layoutSections"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"id\":\"S1\",\"type\":\"layoutSections\"}}")));
        wm.stubFor(post(urlEqualTo("/api/layoutFields"))
                .willReturn(aResponse().withStatus(201).withBody(
                        "{\"data\":{\"id\":\"F1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_layout", Map.of(
                        "name", "ProjectsMain",
                        "collectionName", "projects",
                        "sections", List.of(
                                Map.of("sectionName", "Overview",
                                        "fields", List.of(
                                                Map.of("fieldName", "name"),
                                                Map.of("fieldName", "owner"))),
                                Map.of("sectionName", "Status",
                                        "fields", List.of(Map.of("fieldName", "stage"))))), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/pageLayouts"))
                .withHeader("Authorization", equalTo("Bearer klt_layout_test"))
                .withRequestBody(matchingJsonPath("$.data.attributes.name", equalTo("ProjectsMain")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionName", equalTo("projects"))));
        wm.verify(2, WireMock.postRequestedFor(urlEqualTo("/api/layoutSections")));
        wm.verify(3, WireMock.postRequestedFor(urlEqualTo("/api/layoutFields")));
        // sortOrder defaulting + child references
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/layoutSections"))
                .withRequestBody(matchingJsonPath("$.data.attributes.sectionName", equalTo("Status")))
                .withRequestBody(matchingJsonPath("$.data.attributes.sortOrder", equalTo("1"))));
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/layoutFields"))
                .withRequestBody(matchingJsonPath("$.data.attributes.layoutSectionId", equalTo("S1"))));
    }

    @Test
    void shortCircuitsWhenLayoutCreationFails() {
        wm.stubFor(post(urlEqualTo("/api/pageLayouts"))
                .willReturn(aResponse().withStatus(409).withBody("{}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_layout", Map.of(
                        "name", "L",
                        "collectionName", "projects",
                        "sections", List.of(Map.of("sectionName", "S",
                                "fields", List.of(Map.of("fieldName", "x"))))), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        wm.verify(0, WireMock.postRequestedFor(urlEqualTo("/api/layoutSections")));
        wm.verify(0, WireMock.postRequestedFor(urlEqualTo("/api/layoutFields")));
    }
}
