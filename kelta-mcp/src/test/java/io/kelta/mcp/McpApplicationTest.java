package io.kelta.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.test.context.TestPropertySource;

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
}
