import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setConfigDirForTesting, setLegacyRcForTesting } from '../config/paths.js';
import { saveConfig, setToken } from '../config/store.js';
import type { CommandContext, RegisteredCommand } from '../registry/types.js';
import { mcpCommands } from './mcp.js';

const TOKEN = 'klt_install1234567890';

let dir: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-install-'));
  setConfigDirForTesting(join(dir, '.kelta'));
  setLegacyRcForTesting(join(dir, '.keltarc'));
  saveConfig({
    version: 1,
    defaultProfile: 'prod',
    profiles: { prod: { apiUrl: 'https://api.kelta.io', tenantSlug: 'acme' } },
  });
  setToken('prod', TOKEN);
});

afterEach(() => {
  setConfigDirForTesting();
  setLegacyRcForTesting();
  rmSync(dir, { recursive: true, force: true });
});

const install = mcpCommands.find((command) => command.name === 'install') as RegisteredCommand;

async function run(input: Record<string, unknown>) {
  const ctx = {
    profile: { name: 'prod' },
    global: { profile: 'prod', raw: false, quiet: false, yes: false },
    log: vi.fn(),
    client: undefined,
  } as unknown as CommandContext;
  return install.handler(ctx, install.input.parse(input) as never);
}

describe('kelta mcp install', () => {
  it('claude-code default recommends the stdio bridge', async () => {
    const result = await run({ client: 'claude-code' });
    expect(result.text).toContain('claude mcp add kelta-prod -- kelta mcp serve --profile prod');
  });

  it('claude-desktop and cursor print mergeable stdio JSON', async () => {
    const desktop = await run({ client: 'claude-desktop', toolset: 'admin' });
    const parsedStart = desktop.text?.indexOf('{') ?? 0;
    const parsed = JSON.parse(desktop.text?.slice(parsedStart) ?? '{}') as {
      mcpServers: Record<string, { command: string; args: string[] }>;
    };
    expect(parsed.mcpServers['kelta-prod-admin'].command).toBe('kelta');
    expect(parsed.mcpServers['kelta-prod-admin'].args).toEqual([
      'mcp',
      'serve',
      '--profile',
      'prod',
      '--toolset',
      'admin',
    ]);
    const cursor = await run({ client: 'cursor' });
    expect(cursor.text).toContain('.cursor/mcp.json');
  });

  it('--direct emits the hosted HTTP endpoint with a PAT placeholder, never the token', async () => {
    const result = await run({ client: 'claude-code', direct: true, toolset: 'user' });
    expect(result.text).toContain('https://mcp.kelta.io/acme/mcp/user');
    expect(result.text).toContain('<YOUR_PAT>');
    expect(result.text).not.toContain(TOKEN);
  });

  it('--direct with toolset all falls back to the user endpoint (hosted has no all)', async () => {
    const result = await run({ client: 'generic', direct: true });
    expect(result.text).toContain('/mcp/user');
  });
});
