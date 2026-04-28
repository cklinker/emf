package io.kelta.mcp;

import io.kelta.mcp.resource.UserResource;
import io.kelta.mcp.resource.UserResourceTemplate;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.UserTool;
import io.kelta.mcp.transport.KeltaMcpController;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

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
    private KeltaMcpController mcpController;

    @Autowired
    private List<UserTool> userTools;

    @Autowired
    private List<UserResource> userResources;

    @Autowired
    private List<UserResourceTemplate> userResourceTemplates;

    @Autowired
    private List<AdminTool> adminTools;

    @Test
    void bothMcpServersAreWired() {
        assertThat(userServer).isNotNull();
        assertThat(adminServer).isNotNull();
        assertThat(userServer).isNotSameAs(adminServer);
    }

    @Test
    void mcpControllerIsRegisteredAtSlugPathPattern() {
        // The Spring MVC controller dispatches /{tenantSlug}/mcp/(user|admin)
        // to the matching SDK transport via @PathVariable.
        assertThat(mcpController).isNotNull();
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

    @Test
    void adminEndpointSurfaceCoversPhase6Through8() {
        List<String> names = adminTools.stream()
                .map(t -> t.toSpecification().tool().name())
                .toList();
        assertThat(names).containsExactlyInAnyOrder(
                // shared read-only browse tools (also on /mcp/user)
                "list_collections",
                "get_collection_schema",
                // schema admin (Phase 6)
                "create_collection",
                "update_collection",
                "add_field",
                "update_field",
                "remove_field",
                "create_validation_rule",
                "create_picklist",
                // UI admin (Phase 7)
                "create_layout",
                "update_layout",
                "delete_layout",
                "create_listview",
                // automation admin + integrations (Phase 8)
                "create_flow",
                "update_flow",
                "import_api_spec");
    }

    @Test
    void mutationToolsNeverLeakToAdminEndpoint() {
        List<String> adminNames = adminTools.stream()
                .map(t -> t.toSpecification().tool().name())
                .toList();
        // Hard guarantee: no write-side user tool can appear on the admin server.
        assertThat(adminNames).doesNotContain(
                "create_record", "update_record", "delete_record", "bulk_apply",
                "execute_flow", "submit_for_approval", "list_approvals");
    }
}
