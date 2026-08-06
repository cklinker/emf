// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { setConfigDirForTesting, setLegacyRcForTesting } from '../config/paths.js';
import { saveConfig, setToken } from '../config/store.js';
import { allCommands } from '../registry/registry.js';
import type { RemoteEndpoint } from './remote.js';
import { buildMcpServer, type McpSource } from './server.js';

interface FakeRemote {
  name: string;
  listTools: ReturnType<typeof vi.fn>;
  call: ReturnType<typeof vi.fn>;
}

function fakeRemote(name: string, tools: { name: string; description?: string }[]): FakeRemote {
  return {
    name,
    listTools: vi
      .fn()
      .mockResolvedValue(tools.map((tool) => ({ inputSchema: { type: 'object' }, ...tool }))),
    call: vi.fn().mockResolvedValue({ content: [{ type: 'text', text: `remote:${name}` }] }),
  };
}

async function connect(options: {
  remotes: FakeRemote[];
  source: McpSource;
  enableApiTool?: boolean;
  log?: (message: string) => void;
}) {
  const server = buildMcpServer({
    commands: allCommands,
    remotes: options.remotes as unknown as RemoteEndpoint[],
    source: options.source,
    enableApiTool: options.enableApiTool,
    log: options.log ?? (() => undefined),
  });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test-client', version: '0.0.0' });
  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
  return client;
}

let dir: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-mcpserver-'));
  setConfigDirForTesting(join(dir, '.kelta'));
  setLegacyRcForTesting(join(dir, '.keltarc'));
  saveConfig({
    version: 1,
    defaultProfile: 'prod',
    profiles: { prod: { apiUrl: 'https://api.kelta.io', tenantSlug: 'acme' } },
  });
  setToken('prod', 'klt_srv1234567890abc');
});

afterEach(() => {
  setConfigDirForTesting();
  setLegacyRcForTesting();
  rmSync(dir, { recursive: true, force: true });
});

describe('buildMcpServer', () => {
  it('merges hosted tools (deduped across endpoints) with cli_ local tools', async () => {
    const user = fakeRemote('user', [{ name: 'list_collections' }, { name: 'query_collection' }]);
    const admin = fakeRemote('admin', [
      { name: 'list_collections' },
      { name: 'create_collection' },
    ]);
    const client = await connect({ remotes: [user, admin], source: 'auto' });

    const { tools } = await client.listTools();
    const names = tools.map((tool) => tool.name);
    expect(names.filter((name) => name === 'list_collections')).toHaveLength(1);
    expect(names).toContain('query_collection');
    expect(names).toContain('create_collection');
    expect(names).toContain('cli_metadata_export');
    expect(names).toContain('cli_profile_list');
    expect(names).not.toContain('cli_api');
  });

  it('routes calls: shared tools to the first endpoint, admin-only to admin, cli_ locally', async () => {
    const user = fakeRemote('user', [{ name: 'list_collections' }]);
    const admin = fakeRemote('admin', [
      { name: 'list_collections' },
      { name: 'create_collection' },
    ]);
    const client = await connect({ remotes: [user, admin], source: 'auto' });
    await client.listTools();

    const shared = await client.callTool({ name: 'list_collections', arguments: {} });
    expect((shared.content as { text: string }[])[0].text).toBe('remote:user');
    expect(user.call).toHaveBeenCalledWith('tools/call', {
      name: 'list_collections',
      arguments: {},
    });

    const adminOnly = await client.callTool({
      name: 'create_collection',
      arguments: { name: 'x' },
    });
    expect((adminOnly.content as { text: string }[])[0].text).toBe('remote:admin');

    const local = await client.callTool({ name: 'cli_profile_list', arguments: {} });
    const rows = JSON.parse((local.content as { text: string }[])[0].text) as { name: string }[];
    expect(rows[0].name).toBe('prod');
  });

  it('source auto degrades to local-only with a warning when hosted is down', async () => {
    const down = fakeRemote('user', []);
    down.listTools.mockRejectedValue(new Error('ECONNREFUSED'));
    const log = vi.fn();
    const client = await connect({ remotes: [down], source: 'auto', log });

    const { tools } = await client.listTools();
    expect(tools.some((tool) => tool.name.startsWith('cli_'))).toBe(true);
    expect(tools.some((tool) => !tool.name.startsWith('cli_'))).toBe(false);
    expect(log).toHaveBeenCalledWith(expect.stringContaining('unreachable'));
  });

  it('source remote fails loudly when hosted is down', async () => {
    const down = fakeRemote('user', []);
    down.listTools.mockRejectedValue(new Error('ECONNREFUSED'));
    const client = await connect({ remotes: [down], source: 'remote' });
    await expect(client.listTools()).rejects.toThrow();
  });

  it('source local exposes no remote tools and never contacts the endpoints', async () => {
    const user = fakeRemote('user', [{ name: 'list_collections' }]);
    const client = await connect({ remotes: [user], source: 'local' });
    const { tools } = await client.listTools();
    expect(tools.every((tool) => tool.name.startsWith('cli_'))).toBe(true);
    expect(user.listTools).not.toHaveBeenCalled();
  });

  it('unknown tools return isError without crashing the server', async () => {
    const client = await connect({ remotes: [], source: 'local' });
    await client.listTools();
    const result = await client.callTool({ name: 'nope_tool', arguments: {} });
    expect(result.isError).toBe(true);
  });

  it('local tool failures map to the CLI error envelope inside isError content', async () => {
    const client = await connect({ remotes: [], source: 'local' });
    await client.listTools();
    // metadata export needs a working gateway — the network failure must come
    // back as a structured error payload, not a protocol crash
    const result = await client.callTool({
      name: 'cli_metadata_export',
      arguments: { name: 'x', version: '1' },
    });
    expect(result.isError).toBe(true);
    const payload = JSON.parse((result.content as { text: string }[])[0].text) as {
      error: { code: string };
    };
    expect(payload.error.code).toBeTruthy();
  });
});
