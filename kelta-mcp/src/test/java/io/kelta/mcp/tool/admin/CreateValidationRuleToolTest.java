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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation rules are records of the {@code validation-rules} system
 * collection: the record is REJECTED when {@code errorConditionFormula}
 * evaluates true. (The tool previously posted to a non-existent
 * {@code /api/validationRules} path and documented inverted semantics.)
 */
class CreateValidationRuleToolTest {

    private static final String COLLECTION_ID = "11111111-1111-1111-1111-111111111111";

    private WireMockServer wm;
    private CreateValidationRuleTool tool;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        GatewayHttpClient client = new GatewayHttpClient(
                RestClient.builder(),
                new McpProperties("http://localhost:" + wm.port(), 30, 60_000, null));
        tool = new CreateValidationRuleTool(client);
        RequestPatHolder.set("klt_rule_test");
        wm.stubFor(get(urlEqualTo("/api/collections?filter[name][eq]=projects"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"data\":[{\"type\":\"collections\",\"id\":\"" + COLLECTION_ID + "\"}]}")));
    }

    @AfterEach
    void tearDown() {
        RequestPatHolder.clear();
        wm.stop();
    }

    @Test
    void postsErrorConditionFormulaToKebabRoute() {
        wm.stubFor(post(urlEqualTo("/api/validation-rules"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"r1\"}}")));

        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_validation_rule", Map.of(
                        "collectionName", "projects",
                        "name", "amount-range",
                        "errorConditionFormula", "amountMax < amountMin",
                        "errorMessage", "amountMax must be >= amountMin"), null));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/validation-rules"))
                .withRequestBody(matchingJsonPath("$.data.type", equalTo("validation-rules")))
                .withRequestBody(matchingJsonPath("$.data.attributes.collectionId", equalTo(COLLECTION_ID)))
                .withRequestBody(matchingJsonPath("$.data.attributes.errorConditionFormula", equalTo("amountMax < amountMin")))
                .withRequestBody(matchingJsonPath("$.data.attributes.errorMessage", equalTo("amountMax must be >= amountMin")))
                .withRequestBody(matchingJsonPath("$.data.attributes.evaluateOn", equalTo("CREATE_AND_UPDATE")))
                .withRequestBody(matchingJsonPath("$.data.attributes.severity", equalTo("ERROR")))
                .withRequestBody(matchingJsonPath("$.data.attributes.active", equalTo("true"))));
    }

    @Test
    void negatesLegacyExpressionIntoErrorCondition() {
        wm.stubFor(post(urlEqualTo("/api/validation-rules"))
                .willReturn(aResponse().withStatus(201).withBody("{\"data\":{\"id\":\"r1\"}}")));

        tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_validation_rule", Map.of(
                        "collectionName", "projects",
                        "name", "amount-positive",
                        "expression", "amount > 0",
                        "errorMessage", "amount must be positive"), null));

        // Legacy semantics were "expression must hold" — the error condition is
        // its negation.
        wm.verify(WireMock.postRequestedFor(urlEqualTo("/api/validation-rules"))
                .withRequestBody(matchingJsonPath("$.data.attributes.errorConditionFormula",
                        equalTo("NOT(amount > 0)"))));
    }

    @Test
    void requiresAFormula() {
        CallToolResult result = tool.toSpecification().callHandler().apply(
                null, new CallToolRequest("create_validation_rule", Map.of(
                        "collectionName", "projects",
                        "name", "r",
                        "errorMessage", "m"), null));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }
}
