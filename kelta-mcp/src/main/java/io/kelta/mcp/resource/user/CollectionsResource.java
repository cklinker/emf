package io.kelta.mcp.resource.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.resource.UserResource;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Static resource: a JSON list of all collections in the current tenant.
 * URI: {@code kelta://collections}.
 */
@Component
public class CollectionsResource implements UserResource {

    static final String URI = "kelta://collections";

    private final GatewayHttpClient gateway;

    public CollectionsResource(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncResourceSpecification toSpecification() {
        Resource resource = Resource.builder()
                .uri(URI)
                .name("collections")
                .description("Browseable list of all collections in the current tenant. JSON:API list format.")
                .mimeType("application/json")
                .build();

        return new SyncResourceSpecification(resource, (context, request) -> {
            GatewayHttpClient.Response response = gateway.get("/api/collections");
            String body = response.isSuccess()
                    ? response.body()
                    : "{\"error\":\"gateway returned " + response.status() + "\"}";
            return new ReadResourceResult(List.of(
                    new TextResourceContents(URI, "application/json", body)));
        });
    }
}
