package io.kelta.mcp.error;

import io.kelta.mcp.client.GatewayHttpClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class McpErrorMapperTest {

    @Test
    void success200BecomesResultWithBodyAsText() {
        GatewayHttpClient.Response response = new GatewayHttpClient.Response(
                HttpStatus.OK, "{\"data\":[1,2,3]}");

        CallToolResult result = McpErrorMapper.toResult(response);

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().get(0)).text())
                .isEqualTo("{\"data\":[1,2,3]}");
    }

    @Test
    void notFoundBecomesErrorResultWithStatusInMessage() {
        GatewayHttpClient.Response response = new GatewayHttpClient.Response(
                HttpStatus.NOT_FOUND, "{\"errors\":[{\"detail\":\"unknown collection\"}]}");

        CallToolResult result = McpErrorMapper.toResult(response);

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("404", "unknown collection");
    }

    @Test
    void serverError500BecomesErrorResult() {
        GatewayHttpClient.Response response = new GatewayHttpClient.Response(
                HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        CallToolResult result = McpErrorMapper.toResult(response);

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("500", "boom");
    }

    @Test
    void exceptionBecomesErrorResult() {
        CallToolResult result = McpErrorMapper.fromException(
                new RuntimeException("connect timeout"));

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(((TextContent) result.content().get(0)).text())
                .contains("RuntimeException", "connect timeout");
    }

    @Test
    void redactionScrubsPatTokensFromSuccessBody() {
        GatewayHttpClient.Response response = new GatewayHttpClient.Response(
                HttpStatus.OK, "leaked: klt_AbCdEf123456789012345");

        CallToolResult result = McpErrorMapper.toResult(response);

        assertThat(((TextContent) result.content().get(0)).text())
                .contains("klt_***REDACTED***")
                .doesNotContain("klt_AbCdEf123456789012345");
    }

    @Test
    void redactionScrubsPatTokensFromErrorBody() {
        GatewayHttpClient.Response response = new GatewayHttpClient.Response(
                HttpStatus.UNAUTHORIZED,
                "{\"message\":\"invalid token klt_topsecret999999999999999\"}");

        CallToolResult result = McpErrorMapper.toResult(response);

        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text)
                .contains("klt_***REDACTED***")
                .doesNotContain("klt_topsecret");
    }

    @Test
    void redactionScrubsPatTokensFromExceptionMessage() {
        CallToolResult result = McpErrorMapper.fromException(
                new RuntimeException("auth failed for klt_DEADBEEF1234567890123456"));

        assertThat(((TextContent) result.content().get(0)).text())
                .contains("klt_***REDACTED***")
                .doesNotContain("klt_DEADBEEF");
    }

    @Test
    void redactionLeavesNonPatStringsAlone() {
        assertThat(McpErrorMapper.redact("normal text with no token"))
                .isEqualTo("normal text with no token");
        assertThat(McpErrorMapper.redact(null)).isNull();
    }
}
