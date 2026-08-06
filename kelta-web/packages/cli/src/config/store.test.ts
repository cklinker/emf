import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { getToken, loadConfig, removeToken, renameToken, saveConfig, setToken } from './store.js';
import {
  configPath,
  credentialsPath,
  setConfigDirForTesting,
  setLegacyRcForTesting,
} from './paths.js';

let dir: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'kelta-cli-test-'));
  setConfigDirForTesting(join(dir, '.kelta'));
  setLegacyRcForTesting(join(dir, '.keltarc'));
});

afterEach(() => {
  setConfigDirForTesting();
  setLegacyRcForTesting();
  rmSync(dir, { recursive: true, force: true });
});

describe('config store', () => {
  it('returns an empty config when nothing exists', () => {
    expect(loadConfig()).toEqual({ version: 1, profiles: {} });
  });

  it('round-trips profiles', () => {
    saveConfig({
      version: 1,
      defaultProfile: 'prod',
      profiles: { prod: { apiUrl: 'https://api.kelta.io', tenantSlug: 'acme' } },
    });
    const config = loadConfig();
    expect(config.defaultProfile).toBe('prod');
    expect(config.profiles.prod.tenantSlug).toBe('acme');
  });

  it('survives a corrupt config file', () => {
    saveConfig({ version: 1, profiles: {} });
    writeFileSync(configPath(), 'not json{');
    expect(loadConfig()).toEqual({ version: 1, profiles: {} });
  });
});

describe('credential store', () => {
  it('stores and retrieves tokens per profile', () => {
    setToken('prod', 'klt_secret1');
    setToken('staging', 'klt_secret2');
    expect(getToken('prod')).toBe('klt_secret1');
    expect(getToken('staging')).toBe('klt_secret2');
    expect(getToken('missing')).toBeUndefined();
  });

  it('writes the credentials file with 0600 permissions', () => {
    setToken('prod', 'klt_secret1');
    const mode = statSync(credentialsPath()).mode & 0o777;
    expect(mode).toBe(0o600);
  });

  it('removes and renames tokens', () => {
    setToken('prod', 'klt_secret1');
    renameToken('prod', 'production');
    expect(getToken('prod')).toBeUndefined();
    expect(getToken('production')).toBe('klt_secret1');
    removeToken('production');
    expect(getToken('production')).toBeUndefined();
  });
});

describe('legacy ~/.keltarc migration', () => {
  it('migrates url/tenant/token into the default profile once', () => {
    writeFileSync(
      join(dir, '.keltarc'),
      JSON.stringify({ url: 'https://api.kelta.io/', tenant: 'acme', token: 'klt_legacy1234' })
    );
    const config = loadConfig();
    expect(config.defaultProfile).toBe('default');
    expect(config.profiles.default).toMatchObject({
      apiUrl: 'https://api.kelta.io',
      tenantSlug: 'acme',
      tokenPrefix: 'klt_lega',
    });
    expect(getToken('default')).toBe('klt_legacy1234');
    // config.json now exists — the legacy file no longer wins
    expect(readFileSync(configPath(), 'utf-8')).toContain('acme');
  });

  it('does not overwrite an existing config.json', () => {
    saveConfig({
      version: 1,
      profiles: { prod: { apiUrl: 'https://x', tenantSlug: 'y' } },
    });
    writeFileSync(
      join(dir, '.keltarc'),
      JSON.stringify({ url: 'https://legacy', tenant: 'legacy', token: 'klt_x' })
    );
    expect(loadConfig().profiles.prod).toBeDefined();
    expect(loadConfig().profiles.default).toBeUndefined();
  });

  it('ignores a corrupt legacy file', () => {
    writeFileSync(join(dir, '.keltarc'), '{broken');
    expect(loadConfig()).toEqual({ version: 1, profiles: {} });
  });
});
