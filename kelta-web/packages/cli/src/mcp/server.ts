import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import type { Transport } from '@modelcontextprotocol/sdk/shared/transport.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { mapError } from '../errors.js';
import type { RegisteredCommand } from '../registry/types.js';
import { VERSION } from '../version.js';
import {
  localToolName,
  runLocalCommand,
  selectLocalCommands,
  toMcpTool,
  type LocalToolOptions,
} from './localTools.js';
import type { RemoteEndpoint, RemoteTool } from './remote.js';

export type McpSource = 'auto' | 'remote' | 'local';

export interface McpServerOptions extends LocalToolOptions {
  commands: RegisteredCommand[];
  /** Hosted endpoints to bridge (0-2: user/admin). Empty with source local. */
  remotes: RemoteEndpoint[];
  source: McpSource;
  profileFlag?: string;
  log: (message: string) => void;
}

interface ToolRoute {
  kind: 'local' | 'remote';
  command?: RegisteredCommand;
  endpoint?: RemoteEndpoint;
}

/**
 * Compose the stdio MCP server: hosted kelta-mcp tools bridged per-message
 * (the hosted server is stateless HTTP, so forwarding needs no session) plus
 * `cli_`-prefixed local tools generated from the command registry. Remote
 * names win verbatim; the prefix makes collisions impossible. With
 * `--toolset all` the user and admin endpoints are merged — tools present on
 * both (e.g. list_collections) route to the first endpoint listing them.
 */
export function buildMcpServer(options: McpServerOptions): Server {
  const server = new Server(
    { name: 'kelta-cli', version: VERSION },
    { capabilities: { tools: {} } }
  );

  const localCommands =
    options.source === 'remote' ? [] : selectLocalCommands(options.commands, options);
  const routes = new Map<string, ToolRoute>();
  let remoteTools: { endpoint: RemoteEndpoint; tool: RemoteTool }[] | undefined;

  async function loadRemoteTools(): Promise<{ endpoint: RemoteEndpoint; tool: RemoteTool }[]> {
    if (remoteTools) return remoteTools;
    const collected: { endpoint: RemoteEndpoint; tool: RemoteTool }[] = [];
    for (const endpoint of options.source === 'local' ? [] : options.remotes) {
      try {
        for (const tool of await endpoint.listTools()) {
          // duplicate across endpoints (shared user+admin tools): first wins
          if (!collected.some((entry) => entry.tool.name === tool.name)) {
            collected.push({ endpoint, tool });
          }
        }
      } catch (error) {
        const message = mapError(error).message;
        if (options.source === 'remote') throw error;
        options.log(`Warning: hosted MCP (${endpoint.name}) unreachable — ${message}`);
      }
    }
    remoteTools = collected;
    return collected;
  }

  server.setRequestHandler(ListToolsRequestSchema, async () => {
    const remote = await loadRemoteTools();
    routes.clear();
    for (const { endpoint, tool } of remote) {
      routes.set(tool.name, { kind: 'remote', endpoint });
    }
    for (const command of localCommands) {
      routes.set(localToolName(command), { kind: 'local', command });
    }
    return {
      tools: [
        ...remote.map(({ tool }) => tool),
        ...localCommands.map((command) => toMcpTool(command)),
      ],
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;
    if (routes.size === 0) await loadRemoteTools().then(() => undefined);
    let route = routes.get(name);
    if (!route) {
      // route table may predate a hosted deploy that added tools — resolve lazily
      const command = localCommands.find((entry) => localToolName(entry) === name);
      if (command) route = { kind: 'local', command };
      else {
        const remote = await loadRemoteTools();
        const entry = remote.find(({ tool }) => tool.name === name);
        if (entry) route = { kind: 'remote', endpoint: entry.endpoint };
      }
    }
    if (!route) {
      return {
        content: [{ type: 'text' as const, text: `Unknown tool: ${name}` }],
        isError: true,
      };
    }

    try {
      if (route.kind === 'local' && route.command) {
        const text = await runLocalCommand(route.command, args, options.profileFlag);
        return { content: [{ type: 'text' as const, text }] };
      }
      if (route.endpoint) {
        const result = await route.endpoint.call('tools/call', { name, arguments: args });
        return result as { content: { type: 'text'; text: string }[] };
      }
      throw new Error('unroutable tool');
    } catch (error) {
      const mapped = mapError(error);
      return {
        content: [
          {
            type: 'text' as const,
            text: JSON.stringify({
              error: { code: mapped.code, status: mapped.status, detail: mapped.message },
            }),
          },
        ],
        isError: true,
      };
    }
  });

  return server;
}

export async function connectServer(server: Server, transport: Transport): Promise<void> {
  await server.connect(transport);
}
