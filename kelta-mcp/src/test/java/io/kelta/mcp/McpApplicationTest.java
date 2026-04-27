package io.kelta.mcp;

import io.kelta.mcp.resource.UserResource;
import io.kelta.mcp.resource.UserResourceTemplate;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the application context starts and both MCP server instances
 * are wired with their respective servlet registrations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "kelta.mcp.gateway-url=http://localhost:9999",
        "kelta.mcp.session-ttl-minutes=5",
        "kelta.mcp.tool-timeout-ms=10000"
})
class McpApplicationTest {

    @Autowired
    @Qualifier("userMcpServer")
    private McpSyncServer userServer;

    @Autowired
    @Qualifier("adminMcpServer")
    private McpSyncServer adminServer;

    @Autowired
    private Map<String, ServletRegistrationBean<?>> servletRegistrations;

    @Autowired
    private List<UserTool> userTools;

    @Autowired
    private List<UserResource> userResources;

    @Autowired
    private List<UserResourceTemplate> userResourceTemplates;

    @Test
    void bothMcpServersAreWired() {
        assertThat(userServer).isNotNull();
        assertThat(adminServer).isNotNull();
        assertThat(userServer).isNotSameAs(adminServer);
    }

    @Test
    void bothServletsAreRegisteredWithDistinctMappings() {
        ServletRegistrationBean<?> userServlet = servletRegistrations.get("userMcpServlet");
        ServletRegistrationBean<?> adminServlet = servletRegistrations.get("adminMcpServlet");

        assertThat(userServlet).isNotNull();
        assertThat(adminServlet).isNotNull();
        assertThat(userServlet.getUrlMappings()).contains("/mcp/user", "/mcp/user/*");
        assertThat(adminServlet.getUrlMappings()).contains("/mcp/admin", "/mcp/admin/*");
    }

    @Test
    void allUserToolsAreDiscovered() {
        List<String> names = userTools.stream()
                .map(t -> t.toSpecification().tool().name())
                .toList();
        assertThat(names).containsExactlyInAnyOrder(
                "list_collections",
                "get_collection_schema",
                "query_collection",
                "get_record",
                "search",
                "describe_api",
                "create_record",
                "update_record",
                "delete_record",
                "bulk_apply",
                "execute_flow",
                "get_flow_run",
                "submit_for_approval",
                "list_approvals"
        );
    }

    @Test
    void userResourcesAreDiscovered() {
        List<String> uris = userResources.stream()
                .map(r -> r.toSpecification().resource().uri())
                .toList();
        assertThat(uris).containsExactlyInAnyOrder(
                "kelta://collections",
                "kelta://openapi.json"
        );
    }

    @Test
    void userResourceTemplatesAreDiscovered() {
        List<String> templates = userResourceTemplates.stream()
                .map(t -> t.toSpecification().resourceTemplate().uriTemplate())
                .toList();
        assertThat(templates).containsExactly("kelta://collections/{name}");
    }
}
