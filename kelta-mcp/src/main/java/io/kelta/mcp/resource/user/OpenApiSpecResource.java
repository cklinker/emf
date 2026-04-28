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
 * Static resource: the auto-generated OpenAPI 3.0 spec for the current
 * tenant. URI: {@code kelta://openapi.json}.
 *
 * <p>Note: the spec only covers JSON:API CRUD on collections. Specialized
 * controllers (flows, approvals, bulk) are not in it; use the dedicated
 * tools for those.
 */
@Component
public class OpenApiSpecResource implements UserResource {

    static final String URI = "kelta://openapi.json";

    private final GatewayHttpClient gateway;

    public OpenApiSpecResource(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncResourceSpecification toSpecification() {
        Resource resource = Resource.builder()
                .uri(URI)
                .name("openapi-spec")
                .description("Auto-generated OpenAPI 3.0 spec for this tenant. Covers JSON:API CRUD on every collection.")
                .mimeType("application/json")
                .build();

        return new SyncResourceSpecification(resource, (context, request) -> {
            GatewayHttpClient.Response response = gateway.get("/api/docs/openapi.json");
            String body = response.isSuccess()
                    ? response.body()
                    : "{\"error\":\"gateway returned " + response.status() + "\"}";
            return new ReadResourceResult(List.of(
                    new TextResourceContents(URI, "application/json", body)));
        });
    }
}
