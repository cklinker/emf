package io.kelta.mcp.resource;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;

/**
 * Marker interface for static resources registered on {@code /mcp/user}.
 * "Static" here means a fixed URI (not a template). Use
 * {@link UserResourceTemplate} for URIs with parameters.
 */
public interface UserResource {
    SyncResourceSpecification toSpecification();
}
