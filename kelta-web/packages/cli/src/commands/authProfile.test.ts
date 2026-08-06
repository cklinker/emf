import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { CliError } from '../errors.js';
import { getToken, loadConfig } from '../config/store.js';
import { setConfigDirForTesting, setLegacyRcForTesting } from '../config/paths.js';
import type { CommandContext, RegisteredCommand } from '../registry/types.js';
import { authCommands } from './auth.js';
import { profileCommands } from './profile.js';

let dir: string;
const TOKEN_ONE = 'klt_one4567890abcdef';
const TOKEN_TWO = 'klt_two4567890abcdef';

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-auth-'));
  setConfigDirForTesting(join(dir, '.kelta'));
  setLegacyRcForTesting(join(dir, '.keltarc'));
  // defensive: another test file in the same worker may have exported these
  for (const key of ['KELTA_PROFILE', 'KELTA_URL', 'KELTA_TENANT', 'KELTA_TOKEN']) {
    delete process.env[key];
  }
});

afterEach(() => {
  setConfigDirForTesting();
  setLegacyRcForTesting();
  delete process.env.KELTA_PROFILE;
  rmSync(dir, { recursive: true, force: true });
});

function command(defs: RegisteredCommand[], name: string): RegisteredCommand {
  const def = defs.find((c) => c.name === name);
  if (!def) throw new Error(`no command ${name}`);
  return def;
}

function ctx(profileFlag?: string): CommandContext {
  return {
    profile: { name: profileFlag ?? 'default' },
    global: { profile: profileFlag, raw: false, quiet: false, yes: false },
    log: vi.fn(),
    client: undefined,
  } as unknown as CommandContext;
}

async function run(
  defs: RegisteredCommand[],
  name: string,
  input: Record<string, unknown>,
  profile?: string
) {
  const def = command(defs, name);
  return def.handler(ctx(profile), def.input.parse(input) as never);
}

describe('auth login/logout/status', () => {
  it('login writes profile + credential and sets the default', async () => {
    const result = await run(
      authCommands,
      'login',
      { url: 'https://api.kelta.io/', tenant: 'acme', token: 'klt_abcdefgh123' },
      'prod'
    );
    const config = loadConfig();
    expect(config.defaultProfile).toBe('prod');
    expect(config.profiles.prod).toMatchObject({
      apiUrl: 'https://api.kelta.io',
      tenantSlug: 'acme',
      tokenPrefix: 'klt_abcd',
    });
    expect(getToken('prod')).toBe('klt_abcdefgh123');
    expect(result.message).toContain('prod');
  });

  it('login keeps an existing default profile', async () => {
    await run(authCommands, 'login', { url: 'https://a', tenant: 't1', token: TOKEN_ONE }, 'one');
    await run(authCommands, 'login', { url: 'https://b', tenant: 't2', token: TOKEN_TWO }, 'two');
    expect(loadConfig().defaultProfile).toBe('one');
  });

  it('login rejects a non-URL', async () => {
    const def = command(authCommands, 'login');
    expect(() => def.input.parse({ url: 'not a url', tenant: 'x', token: 'y' })).toThrow();
  });

  it('logout removes the credential and prefix but keeps the profile', async () => {
    await run(authCommands, 'login', { url: 'https://a', tenant: 't', token: 'klt_1' }, 'prod');
    await run(authCommands, 'logout', {}, 'prod');
    expect(getToken('prod')).toBeUndefined();
    expect(loadConfig().profiles.prod.tokenPrefix).toBeUndefined();
    expect(loadConfig().profiles.prod.apiUrl).toBe('https://a');
  });

  it('status reports the resolved profile without leaking the token', async () => {
    await run(
      authCommands,
      'login',
      { url: 'https://a', tenant: 't', token: 'klt_secret9999' },
      'prod'
    );
    const result = await run(authCommands, 'status', {}, 'prod');
    const data = result.data as { token: string | null; authenticated: boolean };
    expect(data.authenticated).toBe(true);
    expect(data.token).toBe('klt_secr…');
    expect(JSON.stringify(result.data)).not.toContain('klt_secret9999');
  });
});

describe('profile lifecycle', () => {
  beforeEach(async () => {
    await run(authCommands, 'login', { url: 'https://a', tenant: 't1', token: TOKEN_ONE }, 'one');
    await run(authCommands, 'login', { url: 'https://b', tenant: 't2', token: TOKEN_TWO }, 'two');
  });

  it('list marks the active profile and never prints full tokens', async () => {
    const result = await run(profileCommands, 'list', {});
    const rows = result.data as { name: string; active: string; token: string }[];
    expect(rows.map((r) => r.name).sort()).toEqual(['one', 'two']);
    expect(rows.find((r) => r.name === 'one')?.active).toBe('*');
    expect(JSON.stringify(rows)).not.toContain(TOKEN_ONE);
  });

  it('use switches the default and validates existence', async () => {
    await run(profileCommands, 'use', { name: 'two' });
    expect(loadConfig().defaultProfile).toBe('two');
    await expect(run(profileCommands, 'use', { name: 'missing' })).rejects.toThrow(CliError);
  });

  it('remove deletes profile + credential and clears the default', async () => {
    await run(profileCommands, 'remove', { name: 'one' });
    expect(loadConfig().profiles.one).toBeUndefined();
    expect(loadConfig().defaultProfile).toBeUndefined();
    expect(getToken('one')).toBeUndefined();
  });

  it('rename moves config, credential, and default pointer', async () => {
    await run(profileCommands, 'rename', { oldName: 'one', newName: 'uno' });
    const config = loadConfig();
    expect(config.profiles.uno.tenantSlug).toBe('t1');
    expect(config.profiles.one).toBeUndefined();
    expect(config.defaultProfile).toBe('uno');
    expect(getToken('uno')).toBe(TOKEN_ONE);
    await expect(
      run(profileCommands, 'rename', { oldName: 'uno', newName: 'two' })
    ).rejects.toThrow(/already exists/);
  });

  it('show resolves a named profile', async () => {
    const result = await run(profileCommands, 'show', { name: 'two' });
    expect(result.data).toMatchObject({ profile: 'two', tenant: 't2' });
  });
});
