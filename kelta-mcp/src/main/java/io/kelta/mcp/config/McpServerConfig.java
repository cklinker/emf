package io.kelta.mcp.config;

import io.kelta.mcp.observe.ObservedToolDecorator;
import io.kelta.mcp.resource.UserResource;
import io.kelta.mcp.resource.UserResourceTemplate;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
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
 */
@Configuration
@EnableScheduling
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);
    private static final String SERVER_NAME = "kelta-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    @Bean(name = "userTransportProvider")
    public HttpServletStreamableServerTransportProvider userTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp/user")
                .build();
    }

    @Bean(name = "adminTransportProvider")
    public HttpServletStreamableServerTransportProvider adminTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp/admin")
                .build();
    }

    @Bean(name = "userMcpServer")
    public McpSyncServer userMcpServer(
            @org.springframework.beans.factory.annotation.Qualifier("userTransportProvider")
            HttpServletStreamableServerTransportProvider transport,
            List<UserTool> userTools,
            List<UserResource> userResources,
            List<UserResourceTemplate> userResourceTemplates,
            ObservedToolDecorator decorator) {
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME + "-user", SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)  // (subscribe, listChanged)
                        .build())
                .build();
        server.addTool(decorator.decorate(pingTool("user"), "user"));
        for (UserTool tool : userTools) {
            McpServerFeatures.SyncToolSpecification spec = decorator.decorate(tool.toSpecification(), "user");
            server.addTool(spec);
            log.info("Registered user tool: {}", spec.tool().name());
        }
        for (UserResource resource : userResources) {
            McpServerFeatures.SyncResourceSpecification spec = resource.toSpecification();
            server.addResource(spec);
            log.info("Registered user resource: {}", spec.resource().uri());
        }
        for (UserResourceTemplate template : userResourceTemplates) {
            McpServerFeatures.SyncResourceTemplateSpecification spec = template.toSpecification();
            server.addResourceTemplate(spec);
            log.info("Registered user resource template: {}", spec.resourceTemplate().uriTemplate());
        }
        return server;
    }

    @Bean(name = "adminMcpServer")
    public McpSyncServer adminMcpServer(
            @org.springframework.beans.factory.annotation.Qualifier("adminTransportProvider")
            HttpServletStreamableServerTransportProvider transport) {
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME + "-admin", SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();
        server.addTool(pingTool("admin"));
        return server;
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> userMcpServlet(
            @org.springframework.beans.factory.annotation.Qualifier("userTransportProvider")
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> reg =
                new ServletRegistrationBean<>(transport, "/mcp/user", "/mcp/user/*");
        reg.setName("mcpUserServlet");
        reg.setLoadOnStartup(1);
        return reg;
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> adminMcpServlet(
            @org.springframework.beans.factory.annotation.Qualifier("adminTransportProvider")
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> reg =
                new ServletRegistrationBean<>(transport, "/mcp/admin", "/mcp/admin/*");
        reg.setName("mcpAdminServlet");
        reg.setLoadOnStartup(1);
        return reg;
    }

    private McpServerFeatures.SyncToolSpecification pingTool(String profile) {
        Tool tool = Tool.builder()
                .name("ping")
                .description("Returns 'pong'. Smoke test that the " + profile + " MCP endpoint is reachable.")
                .inputSchema(Schemas.empty())
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> CallToolResult.builder()
                        .content(List.of(new TextContent("pong (" + profile + ")")))
                        .build())
                .build();
    }
}
