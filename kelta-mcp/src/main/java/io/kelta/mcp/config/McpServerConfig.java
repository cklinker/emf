package io.kelta.mcp.config;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.kelta.mcp.observe.ObservedToolDecorator;
import io.kelta.mcp.observe.PatPropagatingToolDecorator;
import io.kelta.mcp.observe.RateLimitedToolDecorator;
import io.kelta.mcp.resource.UserResource;
import io.kelta.mcp.resource.UserResourceTemplate;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.kelta.mcp.tool.UserTool;
import io.kelta.mcp.transport.HttpServletStatelessServerTransportProvider;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Wires two independent MCP server instances inside a single Spring Boot
 * process — one mounted at {@code /mcp/user} for data-plane work, one at
 * {@code /mcp/admin} for control-plane work.
 *
 * <p>Each endpoint has its own transport provider, its own tool registry,
 * and its own resource registry. Tools registered on one server are NOT
 * visible from the other — this is the structural guarantee that prevents
 * an admin tool from being callable through the user endpoint.
 *
 * <p><b>Transport mode: stateless.</b> We use the SDK's
 * {@code McpStatelessSyncServer} + a custom
 * {@link HttpServletStatelessServerTransportProvider} adapter. The earlier
 * streamable transport kept per-pod session state in memory, so every pod
 * restart wiped the session map and the next request from a connected
 * client returned {@code -32603 Session not found}. Stateless mode avoids
 * the problem by construction — every request carries everything it needs
 * (PAT in {@code Authorization}, tenant slug in the URL), so pod restarts
 * are invisible to clients.
 *
 * <p>Trade-off: server-initiated SSE notifications ({@code listChanged},
 * resource subscriptions) aren't available in stateless mode. We don't use
 * those today and aren't planning to.
 */
@Configuration
@EnableScheduling
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);
    private static final String SERVER_NAME = "kelta-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    @Bean(name = "userTransportProvider")
    public HttpServletStatelessServerTransportProvider userTransportProvider() {
        return new HttpServletStatelessServerTransportProvider(
                new KeltaTransportContextExtractor());
    }

    @Bean(name = "adminTransportProvider")
    public HttpServletStatelessServerTransportProvider adminTransportProvider() {
        return new HttpServletStatelessServerTransportProvider(
                new KeltaTransportContextExtractor());
    }

    @Bean(name = "userMcpServer")
    public McpStatelessSyncServer userMcpServer(
            @org.springframework.beans.factory.annotation.Qualifier("userTransportProvider")
            HttpServletStatelessServerTransportProvider transport,
            List<UserTool> userTools,
            List<UserResource> userResources,
            List<UserResourceTemplate> userResourceTemplates,
            ObservedToolDecorator decorator,
            RateLimitedToolDecorator rateLimiter,
            PatPropagatingToolDecorator patPropagator) {
        McpStatelessSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME + "-user", SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)  // (subscribe, listChanged): both off in stateless
                        .build())
                .build();
        server.addTool(wrap(pingTool("user"), "user", decorator, rateLimiter, patPropagator));
        for (UserTool tool : userTools) {
            McpStatelessServerFeatures.SyncToolSpecification spec = wrap(
                    tool.toSpecification(), "user", decorator, rateLimiter, patPropagator);
            server.addTool(spec);
            log.info("Registered user tool: {}", spec.tool().name());
        }
        for (UserResource resource : userResources) {
            McpStatelessServerFeatures.SyncResourceSpecification spec = resource.toSpecification();
            server.addResource(spec);
            log.info("Registered user resource: {}", spec.resource().uri());
        }
        for (UserResourceTemplate template : userResourceTemplates) {
            McpStatelessServerFeatures.SyncResourceTemplateSpecification spec = template.toSpecification();
            server.addResourceTemplate(spec);
            log.info("Registered user resource template: {}", spec.resourceTemplate().uriTemplate());
        }
        return server;
    }

    @Bean(name = "adminMcpServer")
    public McpStatelessSyncServer adminMcpServer(
            @org.springframework.beans.factory.annotation.Qualifier("adminTransportProvider")
            HttpServletStatelessServerTransportProvider transport,
            List<AdminTool> adminTools,
            ObservedToolDecorator decorator,
            RateLimitedToolDecorator rateLimiter,
            PatPropagatingToolDecorator patPropagator) {
        McpStatelessSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME + "-admin", SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();
        server.addTool(wrap(pingTool("admin"), "admin", decorator, rateLimiter, patPropagator));
        for (AdminTool tool : adminTools) {
            McpStatelessServerFeatures.SyncToolSpecification spec = wrap(
                    tool.toSpecification(), "admin", decorator, rateLimiter, patPropagator);
            server.addTool(spec);
            log.info("Registered admin tool: {}", spec.tool().name());
        }
        return server;
    }

    /**
     * Decorator stack, outermost → innermost:
     *  1. PAT propagator — copies the PAT from McpTransportContext into
     *     the per-thread RequestPatHolder (the gateway client reads it
     *     from there).
     *  2. Rate limiter — keys its bucket on the now-populated PAT;
     *     rate-limited calls return an error before observation runs.
     *  3. Observation — timer + counter, tagged with status.
     *  4. Original tool.
     */
    private static McpStatelessServerFeatures.SyncToolSpecification wrap(
            McpStatelessServerFeatures.SyncToolSpecification original,
            String profile,
            ObservedToolDecorator observed,
            RateLimitedToolDecorator rateLimited,
            PatPropagatingToolDecorator patPropagator) {
        return patPropagator.decorate(
                rateLimited.decorate(
                        observed.decorate(original, profile),
                        profile));
    }

    // Note: SDK transports are NOT registered as standalone servlets via
    // ServletRegistrationBean. KeltaMcpController dispatches to them via
    // @RequestMapping("/{tenantSlug}/mcp/{profile:user|admin}") so the slug
    // stays in the URL end-to-end. The transports are still Spring beans
    // (above) so McpServer can attach them to its tool/resource registries
    // — they're just not bound to a servlet container path.

    private McpStatelessServerFeatures.SyncToolSpecification pingTool(String profile) {
        Tool tool = Tool.builder()
                .name("ping")
                .title("Ping (" + profile + ")")
                .description("Returns 'pong'. Smoke test that the " + profile + " MCP endpoint is reachable.")
                .inputSchema(Schemas.empty())
                .annotations(ToolHints.read())
                .build();

        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> CallToolResult.builder()
                        .content(List.of(new TextContent("pong (" + profile + ")")))
                        .build())
                .build();
    }
}
