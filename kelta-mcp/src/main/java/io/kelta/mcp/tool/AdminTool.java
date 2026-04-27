package io.kelta.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

/**
 * Marker interface for tools registered on the {@code /mcp/admin}
 * endpoint (control-plane: define collections/fields/layouts/flows).
 *
 * <p>Read-only browse tools may implement BOTH {@link UserTool} and
 * {@link AdminTool} so they appear on both endpoints —
 * {@code list_collections} and {@code get_collection_schema} are the
 * canonical examples. Mutation tools live in only one list.
 *
 * <p>Spring autowires {@code List<AdminTool>} and the bean
 * registration code on each server walks its own list — there is no
 * way for a write-side user tool to leak into the admin surface (or
 * vice versa) by accident.
 */
public interface AdminTool {
    SyncToolSpecification toSpecification();
}
