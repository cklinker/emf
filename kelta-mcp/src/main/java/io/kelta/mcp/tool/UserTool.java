package io.kelta.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

/**
 * Marker interface for tools registered on the {@code /mcp/user}
 * endpoint. Each user tool exposes a single MCP tool specification.
 *
 * <p>Admin-side tools implement a separate {@code AdminTool}
 * interface so the two registries are structurally distinct —
 * a Spring autowire of {@code List<UserTool>} cannot accidentally
 * pick up an admin tool.
 */
public interface UserTool {
    SyncToolSpecification toSpecification();
}
