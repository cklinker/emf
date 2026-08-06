import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import { requireAuthenticated, resolveProfile } from '../config/resolve.js';
import { CliError, EXIT } from '../errors.js';
import { deriveMcpUrl, RemoteEndpoint, type Toolset } from '../mcp/remote.js';
import { buildMcpServer, connectServer, type McpSource } from '../mcp/server.js';
import { defineCommand, type RegisteredCommand } from '../registry/types.js';
import { BUILD_TARGET } from '../version.js';

const TOOLSETS: Toolset[] = ['user', 'admin'];

/**
 * Command a GUI MCP client should launch. Desktop clients (Claude Desktop,
 * Cursor) are started by the OS launcher, NOT a login shell, so they do not
 * inherit `~/.local/bin` on PATH — a bare `kelta` fails to spawn with no
 * useful error. A released binary knows its own absolute path; a dev build
 * (node running dist/) falls back to the bare name, since execPath there is
 * the node interpreter.
 */
function launcherCommand(): string {
  return BUILD_TARGET === 'dev' ? 'kelta' : process.execPath;
}

function resolveToolsets(toolset: string): Toolset[] {
  return toolset === 'all' ? TOOLSETS : [toolset as Toolset];
}

function resolveRemotes(
  profileFlag: string | undefined,
  toolset: string,
  mcpUrlFlag: string | undefined,
  source: McpSource
): RemoteEndpoint[] {
  if (source === 'local') return [];
  const profile = requireAuthenticated(resolveProfile(profileFlag));
  const mcpUrl = mcpUrlFlag ?? deriveMcpUrl(profile.apiUrl);
  if (!mcpUrl) {
    throw new CliError(
      `Cannot derive the hosted MCP URL from ${profile.apiUrl} — pass --mcp-url (e.g. https://mcp.kelta.io)`,
      { code: 'MISSING_MCP_URL', exitCode: EXIT.USAGE }
    );
  }
  return resolveToolsets(toolset).map(
    (set) => new RemoteEndpoint(mcpUrl, profile.tenantSlug, set, profile.token)
  );
}

const serve = defineCommand({
  group: 'mcp',
  name: 'serve',
  summary: 'Run a stdio MCP server: hosted kelta-mcp tools bridged + cli_ local tools',
  requiresAuth: false, // source=local works offline; remote sources check the profile themselves
  options: [
    { flag: '--toolset <set>', description: 'user|admin|all', default: 'all' },
    { flag: '--source <source>', description: 'auto|remote|local', default: 'auto' },
    { flag: '--mcp-url <url>', description: 'Hosted MCP base URL (default: api.→mcp. derivation)' },
    {
      flag: '--enable-api-tool',
      description: 'Expose the raw cli_api escape hatch (off by default)',
    },
  ],
  input: z.object({
    toolset: z.enum(['user', 'admin', 'all']).default('all'),
    source: z.enum(['auto', 'remote', 'local']).default('auto'),
    mcpUrl: z.string().url().optional(),
    enableApiTool: z.boolean().default(false),
  }),
  handler: async (ctx, input) => {
    // lazy import: registry.ts imports this module (cycle-safe at call time)
    const { allCommands } = await import('../registry/registry.js');
    const remotes = resolveRemotes(ctx.global.profile, input.toolset, input.mcpUrl, input.source);
    const server = buildMcpServer({
      commands: allCommands,
      remotes,
      source: input.source,
      profileFlag: ctx.global.profile,
      enableApiTool: input.enableApiTool,
      log: ctx.log, // stderr only — stdout carries MCP frames exclusively
    });
    ctx.log(
      `kelta MCP server on stdio (toolset ${input.toolset}, source ${input.source}, ` +
        `${String(remotes.length)} hosted endpoint(s))`
    );
    const transport = new StdioServerTransport();
    await connectServer(server, transport);
    // serve until the client closes stdin
    await new Promise<void>((resolve) => {
      transport.onclose = () => resolve();
      process.stdin.on('close', () => resolve());
    });
    return {};
  },
});

const install = defineCommand({
  group: 'mcp',
  name: 'install',
  summary: 'Print MCP client configuration for this profile (stdio bridge by default)',
  requiresAuth: false,
  positionals: [
    { name: 'client', description: 'claude-code|claude-desktop|cursor|generic', required: true },
  ],
  options: [
    { flag: '--toolset <set>', description: 'user|admin|all', default: 'all' },
    { flag: '--direct', description: 'Hosted HTTP config instead of the stdio bridge' },
  ],
  input: z.object({
    client: z.enum(['claude-code', 'claude-desktop', 'cursor', 'generic']),
    toolset: z.enum(['user', 'admin', 'all']).default('all'),
    direct: z.boolean().default(false),
  }),
  handler: (ctx, input) => {
    const profile = resolveProfile(ctx.global.profile);
    const name = `kelta-${profile.name}${input.toolset === 'all' ? '' : `-${input.toolset}`}`;
    const serveArgs = [
      'mcp',
      'serve',
      '--profile',
      profile.name,
      ...(input.toolset === 'all' ? [] : ['--toolset', input.toolset]),
    ];

    if (input.direct) {
      // the hosted server has no "all": one endpoint per toolset
      const toolset = input.toolset === 'all' ? 'user' : input.toolset;
      const mcpUrl = profile.apiUrl ? deriveMcpUrl(profile.apiUrl) : undefined;
      const url = `${mcpUrl ?? 'https://mcp.kelta.io'}/${profile.tenantSlug ?? '<tenant>'}/mcp/${toolset}`;
      const text =
        input.client === 'claude-code'
          ? `claude mcp add ${name} --transport http --url ${url} \\\n  --header "Authorization: Bearer <YOUR_PAT>"\n` +
            '# Replace <YOUR_PAT> with a token from: kelta token create --name mcp\n'
          : JSON.stringify(
              {
                mcpServers: {
                  [name]: {
                    type: 'http',
                    url,
                    headers: { Authorization: 'Bearer <YOUR_PAT>' },
                  },
                },
              },
              null,
              2
            ) + '\n# Replace <YOUR_PAT>; tokens come from: kelta token create --name mcp\n';
      return Promise.resolve({ text });
    }

    const command = launcherCommand();
    const stdioConfig = JSON.stringify(
      { mcpServers: { [name]: { command, args: serveArgs } } },
      null,
      2
    );
    const text =
      input.client === 'claude-code'
        ? `claude mcp add ${name} -- ${command} ${serveArgs.join(' ')}\n`
        : input.client === 'claude-desktop'
          ? `# Merge into claude_desktop_config.json (Claude Desktop → Settings → Developer):\n${stdioConfig}\n`
          : input.client === 'cursor'
            ? `# Merge into .cursor/mcp.json:\n${stdioConfig}\n`
            : stdioConfig + '\n';
    return Promise.resolve({ text });
  },
});

export const mcpCommands: RegisteredCommand[] = [serve, install];
