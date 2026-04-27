package io.kelta.mcp.resource;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;

/**
 * Marker interface for templated resources registered on {@code /mcp/user}.
 * The URI carries variable segments (e.g. {@code kelta://collections/{name}}).
 */
public interface UserResourceTemplate {
    SyncResourceTemplateSpecification toSpecification();
}
