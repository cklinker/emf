# kelta-mcp

Model Context Protocol (MCP) server for the Kelta platform. Exposes platform operations to MCP clients (e.g. Claude Code) over HTTP, authenticated by the user's Personal Access Token.

## Endpoints

Two MCP server instances run inside one Spring Boot process:

| Path | Profile | Purpose |
|------|---------|---------|
| `/mcp/user` | data-plane | CRUD records, run flows, approvals, search |
| `/mcp/admin` | control-plane | Define collections/fields/layouts/flows/etc. |

Each endpoint has its own immutable tool registry — admin tools are not callable from `/mcp/user` (or vice versa).

## Auth

Every request to `/mcp/**` must carry `Authorization: Bearer klt_*`. The `McpAuthFilter` does a presence/prefix check only — the actual cryptographic validation happens at the gateway when tool calls are forwarded. The filter also strips client-supplied `X-Tenant-ID` / `X-Tenant-Slug` / `X-User-Id` headers so a client cannot impersonate another tenant; tenant is derived by the gateway from the PAT.

PATs are kept in `PatSessionStore` keyed by MCP session id, evicted on idle timeout.

## Package Layout

```
io.kelta.mcp/
  config/          ← McpServerConfig (two server instances), McpProperties, WebConfig
  auth/            ← McpAuthFilter, PatSessionStore
  transport/       ← KeltaMcpEndpoints (path mounting)
  tool/
    shared/        ← JsonApiClient, AttributeMapper, ResponseShaper
    user/          ← QueryCollectionTool, CreateRecordTool, ExecuteFlowTool, ...
    admin/         ← CreateCollectionTool, CreateLayoutTool, CreateFlowTool, ...
  resource/        ← MCP Resources for browsable schema/openapi
  client/          ← GatewayHttpClient (in-cluster: http://emf-gateway:80)
  error/           ← McpErrorMapper (gateway 4xx/5xx → MCP error result, redacts klt_)
```

## Key Patterns

### MCP server wiring
Each profile is wired with its own `HttpServletStreamableServerTransportProvider` (from `io.modelcontextprotocol.sdk:mcp`) and its own `McpSyncServer`. Tools are added to the server in `@Bean` methods so registration is static and visible.

```java
McpSyncServer server = McpServer.sync(transport)
    .serverInfo("kelta-mcp-user", "1.0.0")
    .capabilities(ServerCapabilities.builder().tools(true).build())
    .build();
server.addTool(new ListCollectionsTool().toSpecification(...));
```

### Tool definition
Each tool is a class that produces a `McpServerFeatures.SyncToolSpecification`. The call handler builds an HTTP request, forwards through `GatewayHttpClient` (PAT pulled from `PatSessionStore` via the MCP session id), and shapes the JSON:API response into MCP `TextContent` / `JsonContent`.

### No DB
kelta-mcp is stateless. The runtime-core auto-configurations (`KeltaRuntimeAutoConfiguration`, `EncryptionAutoConfiguration`) are excluded in `McpApplication`. Component scan is restricted to `io.kelta.mcp` so we only depend on runtime-core for type definitions (CollectionDefinition, FieldDefinition, FieldType).

## Configuration

```yaml
kelta:
  mcp:
    gateway-url: http://emf-gateway:80
    session-ttl-minutes: 30
    tool-timeout-ms: 60000
```

## Running Tests

```bash
mvn test -f kelta-mcp/pom.xml                                       # All tests
mvn test -f kelta-mcp/pom.xml -Dtest=McpAuthFilterTest              # Single class
mvn test -f kelta-mcp/pom.xml -Dtest="*Tool*"                        # Pattern
```

## Local Smoke Test

```bash
mvn spring-boot:run -f kelta-mcp/pom.xml

claude mcp add kelta-local --transport http \
  --url http://localhost:8080/mcp/user \
  --header "Authorization: Bearer klt_smoke_test_token"
# In a Claude Code session, ask: "use the kelta-local MCP server to call ping"
```

## Plan

Full plan and phases live in `~/.claude/plans/i-would-like-to-kind-rivest.md`.
