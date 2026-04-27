package io.kelta.mcp.error;

import io.kelta.mcp.client.GatewayHttpClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Translates gateway HTTP responses into MCP {@link CallToolResult}s.
 *
 * <p>Normal 2xx responses become regular tool results carrying the body
 * verbatim as text. 4xx/5xx responses become {@code isError: true} tool
 * results with a short, human-readable message.
 *
 * <p>Token redaction: the gateway should never echo a PAT back, but we
 * defensively scrub any {@code klt_…} substring before emitting MCP
 * content. This is a belt-and-suspenders measure — if a regression in
 * the gateway ever exposes the token in an error message, it will not
 * leak through us.
 */
public final class McpErrorMapper {

    private static final Pattern PAT_PATTERN = Pattern.compile("klt_[A-Za-z0-9]+");

    private McpErrorMapper() {}

    public static CallToolResult toResult(GatewayHttpClient.Response response) {
        String body = redact(response.body());
        if (response.isSuccess()) {
            return CallToolResult.builder()
                    .content(List.of(new TextContent(body == null ? "" : body)))
                    .build();
        }
        int statusCode = response.status() == null ? 0 : response.status().value();
        String message = "Gateway returned HTTP " + statusCode
                + (body == null || body.isBlank() ? "" : " — " + body);
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent(message)))
                .build();
    }

    public static CallToolResult fromException(Throwable t) {
        String message = redact(t.getClass().getSimpleName() + ": " + t.getMessage());
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent("Tool call failed — " + message)))
                .build();
    }

    static String redact(String input) {
        if (input == null) return null;
        return PAT_PATTERN.matcher(input).replaceAll("klt_***REDACTED***");
    }
}
